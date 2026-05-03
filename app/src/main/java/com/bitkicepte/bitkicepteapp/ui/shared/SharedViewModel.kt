package com.bitkicepte.bitkicepteapp.ui.shared

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitkicepte.bitkicepteapp.data.local.database.AppDatabase
import com.bitkicepte.bitkicepteapp.data.local.entity.ControlMode
import com.bitkicepte.bitkicepteapp.data.local.entity.PlantProfile
import com.bitkicepte.bitkicepteapp.data.network.WiFiTcpForegroundService
import com.bitkicepte.bitkicepteapp.data.repository.GreenhouseRepository
import com.bitkicepte.bitkicepteapp.domain.engine.OptimizationEngine
import com.bitkicepte.bitkicepteapp.domain.model.ActuatorState
import com.bitkicepte.bitkicepteapp.domain.model.SensorData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class SharedViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    val repo = GreenhouseRepository(
        db.sensorReadingDao(),
        db.actuatorEventDao(),
        db.energyDao()
    )
    private val plantProfileDao = db.plantProfileDao()

    // ── Bağlantı ─────────────────────────────────────────────────────────
    val connected: StateFlow<Boolean> = repo.connected
    val latestSensor: StateFlow<SensorData?> = repo.latestSensor
    val sensorTick: SharedFlow<SensorData> = repo.sensorTick
    val actuatorState: StateFlow<ActuatorState> = repo.actuatorState

    // ── Canlı grafik buffer (fragment'tan bağımsız, 1 dakika pencere) ─────
    val liveBuffer = ArrayDeque<SensorData>()
    private val LIVE_WINDOW_MS = 60_000L

    init {
        repo.sensorTick.onEach { data ->
            val now = System.currentTimeMillis()
            liveBuffer.addLast(data)
            while (liveBuffer.isNotEmpty() && now - liveBuffer.first().timestamp > LIVE_WINDOW_MS) {
                liveBuffer.removeFirst()
            }
        }.launchIn(viewModelScope)

        // Profil listesi yüklenince seçili profil hâlâ null ise ilk profili otomatik seç
        viewModelScope.launch {
            val profiles = allProfiles.filter { it.isNotEmpty() }.first()
            if (_selectedProfile.value == null) {
                _selectedProfile.value = profiles.first()
            }
        }
    }

    private val _controlMode = MutableStateFlow(ControlMode.MANUAL)
    val controlMode: StateFlow<ControlMode> = _controlMode.asStateFlow()

    // ── Bitki Profili ─────────────────────────────────────────────────────
    val allProfiles: StateFlow<List<PlantProfile>> = plantProfileDao.getAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedProfile = MutableStateFlow<PlantProfile?>(null)
    val selectedProfile: StateFlow<PlantProfile?> = _selectedProfile.asStateFlow()

    fun addCustomProfile(profile: PlantProfile) {
        viewModelScope.launch {
            val newId = plantProfileDao.insert(profile.copy(id = 0, isCustom = true))
            plantProfileDao.getById(newId.toInt())?.let { selectProfile(it) }
        }
    }

    fun deleteCustomProfile(id: Int) {
        viewModelScope.launch {
            plantProfileDao.deleteCustomById(id)
            if (_selectedProfile.value?.id == id) _selectedProfile.value = null
        }
    }

    fun selectProfile(profile: PlantProfile) {
        _selectedProfile.value = profile
        // Profili ESP32'ye de gönder (kendi karar motoru kullansın)
        viewModelScope.launch { repo.sendProfile(profile) }
        // Aktif modda kararı yeniden çalıştır
        when (_controlMode.value) {
            ControlMode.SMART -> runSmartDecision()
            ControlMode.AUTO  -> runAutoDecision()
            else -> {}
        }
    }

    // ── DLI birikimi ──────────────────────────────────────────────────────
    private var dliAccumulated = 0f
    private var lastDliTs = System.currentTimeMillis()

    // Hava tahmini: ESP32'den TCP paketiyle gelir (repo.forecastTemp3h)
    private val forecastTemp3h: Float?
        get() = repo.forecastTemp3h.value

    // Son karar sebebi (AUTO ve SMART için)
    private val _smartReason = MutableStateFlow("")
    val smartReason: StateFlow<String> = _smartReason.asStateFlow()

    // ── Bağlantı yönetimi ─────────────────────────────────────────────────

    fun connect(host: String, port: Int) {
        viewModelScope.launch {
            val ok = repo.connect(host, port)
            if (ok) WiFiTcpForegroundService.start(getApplication(), host, port)
        }
    }

    fun disconnect() {
        repo.disconnect()
        WiFiTcpForegroundService.stop(getApplication())
    }

    fun resetAllData() {
        liveBuffer.clear()
        viewModelScope.launch { repo.resetAllData() }
    }

    // ── Mod ──────────────────────────────────────────────────────────────

    fun setMode(mode: ControlMode) {
        _controlMode.value = mode
        when (mode) {
            ControlMode.SMART -> runSmartDecision()
            ControlMode.AUTO  -> runAutoDecision()
            else -> {}
        }
    }

    // ── Aktüatör (Manuel mod) ─────────────────────────────────────────────

    fun setActuator(state: ActuatorState) {
        viewModelScope.launch {
            repo.setActuatorState(state.copy(controlMode = _controlMode.value))
        }
    }

    // ── Her yeni sensör verisinde tetiklenir ──────────────────────────────

    fun onNewSensorData(data: SensorData) {
        updateDli(data)
        when (_controlMode.value) {
            ControlMode.SMART -> runSmartDecision()
            ControlMode.AUTO  -> runAutoDecision()
            else -> {}
        }
    }

    private fun runSmartDecision() {
        val sensor = latestSensor.value ?: return
        viewModelScope.launch {
            val result = OptimizationEngine.decide(
                sensor          = sensor,
                forecastTemp3h  = forecastTemp3h,
                dliAccumulated  = dliAccumulated,
                profile         = _selectedProfile.value
            )
            _smartReason.value = result.reasons.joinToString("\n")
            repo.setActuatorState(result.state, result.reasons.joinToString("; "))
        }
    }

    private fun runAutoDecision() {
        val sensor = latestSensor.value ?: return
        viewModelScope.launch {
            val result = OptimizationEngine.autoDecide(
                sensor  = sensor,
                profile = _selectedProfile.value
            )
            _smartReason.value = result.reasons.joinToString("\n")
            repo.setActuatorState(result.state, result.reasons.joinToString("; "))
        }
    }

    private fun updateDli(data: SensorData) {
        val now = System.currentTimeMillis()
        val durHours = (now - lastDliTs) / 3_600_000f
        lastDliTs = now
        // Lux → µmol/m²/s: 1 lux ≈ 0.0185 µmol/m²/s (beyaz LED yaklaşımı)
        dliAccumulated += data.luxValue * durHours * 0.0185f
    }

    // ── Enerji akışları ───────────────────────────────────────────────────

    fun getTodayConsumptionWh(dayStartMs: Long) = repo.getTodayConsumptionWh(dayStartMs)
    fun getTodaySolarWh(dayStartMs: Long)        = repo.getTodaySolarWh(dayStartMs)
    fun getLast7Days()                            = repo.getLast7Days()

    suspend fun getSensorSince(since: Long)       = repo.getSensorSince(since)
    suspend fun get15MinBuckets(since: Long)      = repo.get15MinBuckets(since)
    suspend fun get6HourBuckets(since: Long)      = repo.get6HourBuckets(since)
    suspend fun getTodayBreakdown(dayStart: Long) = repo.getTodayBreakdown(dayStart)
}

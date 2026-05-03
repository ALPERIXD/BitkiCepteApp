package com.bitkicepte.bitkicepteapp.data.repository

import com.bitkicepte.bitkicepteapp.data.local.dao.ActuatorEventDao
import com.bitkicepte.bitkicepteapp.data.local.dao.EnergyDao
import com.bitkicepte.bitkicepteapp.data.local.dao.SensorReadingDao
import com.bitkicepte.bitkicepteapp.data.local.entity.ActuatorEvent
import com.bitkicepte.bitkicepteapp.data.local.entity.ActuatorType
import com.bitkicepte.bitkicepteapp.data.local.entity.ControlMode
import com.bitkicepte.bitkicepteapp.data.local.entity.EnergyReading
import com.bitkicepte.bitkicepteapp.data.local.entity.SensorReading
import com.bitkicepte.bitkicepteapp.data.network.TcpSocketService
import com.bitkicepte.bitkicepteapp.domain.engine.EnergyEstimator
import com.bitkicepte.bitkicepteapp.domain.model.ActuatorState
import com.bitkicepte.bitkicepteapp.domain.model.SensorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class GreenhouseRepository(
    private val sensorDao: SensorReadingDao,
    private val actuatorDao: ActuatorEventDao,
    private val energyDao: EnergyDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tcp = TcpSocketService()

    // ── Public state ─────────────────────────────────────────────────────
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _latestSensor = MutableStateFlow<SensorData?>(null)
    val latestSensor: StateFlow<SensorData?> = _latestSensor.asStateFlow()

    private val _actuatorState = MutableStateFlow(ActuatorState())
    val actuatorState: StateFlow<ActuatorState> = _actuatorState.asStateFlow()

    // ── Dahili ──────────────────────────────────────────────────────────
    private var dataJob: Job? = null
    private var lastEnergyTs = System.currentTimeMillis()

    // ── Bağlantı ─────────────────────────────────────────────────────────

    suspend fun connect(host: String, port: Int): Boolean {
        val result = tcp.connect(host, port)
        return if (result is TcpSocketService.ConnectResult.Success) {
            _connected.value = true
            startCollection()
            cleanOldData()
            true
        } else false
    }

    fun disconnect() {
        dataJob?.cancel()
        tcp.disconnect()
        _connected.value = false
    }

    // ── Veri toplama ─────────────────────────────────────────────────────

    private fun startCollection() {
        dataJob?.cancel()
        dataJob = scope.launch {
            tcp.dataStream().collect { data ->
                _latestSensor.value = data
                persistSensor(data)
                updateEnergy(data)
            }
        }
    }

    private suspend fun persistSensor(d: SensorData) {
        sensorDao.insert(SensorReading(
            timestamp = d.timestamp,
            temperatureC = d.temperatureC,
            humidityPercent = d.humidityPercent,
            soilMoisturePercent = d.soilMoisturePercent,
            luxValue = d.luxValue,
            solarVoltage = d.solarVoltage,
            solarCurrentA = d.solarCurrentA,
            solarPowerW = d.solarPowerW
        ))
    }

    private suspend fun updateEnergy(d: SensorData) {
        val now = System.currentTimeMillis()
        val durSec = (now - lastEnergyTs) / 1000f
        lastEnergyTs = now
        if (durSec <= 0f) return

        val s = _actuatorState.value
        energyDao.insertReading(EnergyReading(
            fanWh             = EnergyEstimator.toWh(EnergyEstimator.fanWatts(s.fanDuty), durSec),
            ledWh             = EnergyEstimator.toWh(EnergyEstimator.ledWatts(s.ledDuty), durSec),
            pumpWh            = EnergyEstimator.toWh(EnergyEstimator.pumpWatts(s.pumpDuty), durSec),
            solarProductionWh = EnergyEstimator.toWh(d.solarPowerW, durSec)
        ))
    }

    // ── Aktüatör ─────────────────────────────────────────────────────────

    suspend fun setActuatorState(state: ActuatorState, reason: String = "") {
        _actuatorState.value = state
        if (tcp.isConnected) tcp.sendCommand(state)

        // Kayıt
        listOf(
            Triple(ActuatorType.FAN,  state.fanDuty,  state.fanOn),
            Triple(ActuatorType.LED,  state.ledDuty,  state.ledOn),
            Triple(ActuatorType.PUMP, state.pumpDuty, state.pumpOn)
        ).forEach { (type, duty, on) ->
            actuatorDao.insert(ActuatorEvent(
                actuatorType  = type,
                pwmDuty       = duty,
                isOn          = on,
                controlMode   = state.controlMode,
                triggerReason = reason
            ))
        }
    }

    // ── DAO erişimleri ────────────────────────────────────────────────────

    fun getSensorHistory(limit: Int = 120)          = sensorDao.getLatest(limit)
    fun getTodayConsumptionWh(dayStartMs: Long)      = energyDao.getTodayConsumptionWh(dayStartMs)
    fun getTodaySolarWh(dayStartMs: Long)            = energyDao.getTodaySolarWh(dayStartMs)
    fun getLast7Days()                               = energyDao.getLast7Days()
    suspend fun getSensorSince(since: Long)          = sensorDao.getSince(since)
    suspend fun get15MinBuckets(since: Long)         = sensorDao.get15MinBuckets(since)
    suspend fun get6HourBuckets(since: Long)         = sensorDao.get6HourBuckets(since)
    suspend fun getTodayBreakdown(dayStart: Long)    = energyDao.getTodayBreakdown(dayStart)

    private fun cleanOldData() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        scope.launch {
            sensorDao.deleteOlderThan(cutoff)
            actuatorDao.deleteOlderThan(cutoff)
            energyDao.deleteOlderThan(cutoff)
        }
    }
}

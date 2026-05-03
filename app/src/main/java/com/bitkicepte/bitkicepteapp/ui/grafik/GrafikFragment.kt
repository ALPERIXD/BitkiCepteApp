package com.bitkicepte.bitkicepteapp.ui.grafik

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bitkicepte.bitkicepteapp.R
import com.bitkicepte.bitkicepteapp.data.local.entity.SensorReading
import com.bitkicepte.bitkicepteapp.databinding.FragmentGrafikBinding
import com.bitkicepte.bitkicepteapp.domain.engine.EnergyEstimator
import com.bitkicepte.bitkicepteapp.ui.shared.SharedViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class GrafikFragment : Fragment() {

    private var _binding: FragmentGrafikBinding? = null
    private val binding get() = _binding!!
    private val vm: SharedViewModel by activityViewModels()

    private var selectedMode = "LIVE"
    private var liveJob: Job? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentGrafikBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCharts()
        setupTimeFilter()
        setupResetButton()
        startLiveMode()
    }

    // ── Sıfırlama butonu ──────────────────────────────────────────────────

    private fun setupResetButton() {
        binding.btnResetData.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Verileri Sıfırla")
                .setMessage("Tüm sensör, enerji ve grafik verileri silinecek. Emin misin?")
                .setPositiveButton("Sıfırla") { _, _ ->
                    vm.resetAllData()
                    clearCharts()
                }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
    }

    // ── Tab seçimi ────────────────────────────────────────────────────────

    private fun setupTimeFilter() {
        binding.toggleTime.check(R.id.btnLive)
        binding.toggleTime.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            liveJob?.cancel()
            liveJob = null
            selectedMode = when (checkedId) {
                R.id.btnLive -> "LIVE"
                R.id.btn24h  -> "24"
                R.id.btn7d   -> "168"
                else         -> "LIVE"
            }
            if (selectedMode == "LIVE") startLiveMode() else loadDbData()
        }
    }

    // ── Canlı mod ─────────────────────────────────────────────────────────

    private fun startLiveMode() {
        // Buffer ViewModel'de yaşıyor — fragment geçişlerinde korunur
        if (vm.liveBuffer.isNotEmpty()) renderLive()
        liveJob = viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.sensorTick.collect { renderLive() }
            }
        }
    }

    private fun renderLive() {
        if (vm.liveBuffer.isEmpty()) return
        val t0 = vm.liveBuffer.first().timestamp

        // X = saniye offseti (0'dan başlar, sola kayar)
        fun toX(ts: Long) = ((ts - t0) / 1_000f)

        updateChart(
            binding.chartTempHum,
            lineSet(vm.liveBuffer.map { Entry(toX(it.timestamp), it.temperatureC) },    "Sicaklik °C", Color.parseColor("#E65100")),
            lineSet(vm.liveBuffer.map { Entry(toX(it.timestamp), it.humidityPercent) }, "Nem %",       Color.parseColor("#1565C0"))
        )
        updateChart(
            binding.chartSoilLight,
            lineSet(vm.liveBuffer.map { Entry(toX(it.timestamp), it.soilMoisturePercent) }, "Toprak %", Color.parseColor("#2E7D32")),
            lineSet(vm.liveBuffer.map { Entry(toX(it.timestamp), it.luxValue / 10f) },      "Isik /10", Color.parseColor("#F9A825"))
        )
        updateChart(
            binding.chartSolar,
            lineSet(vm.liveBuffer.map { Entry(toX(it.timestamp), it.solarPowerW) }, "Solar W", Color.parseColor("#F9A825"))
        )
        val state  = vm.actuatorState.value
        val totalW = EnergyEstimator.fanWatts(state.fanDuty) +
                     EnergyEstimator.ledWatts(state.ledDuty) +
                     EnergyEstimator.pumpWatts(state.pumpDuty)
        updateChart(
            binding.chartConsumption,
            lineSet(vm.liveBuffer.map { Entry(toX(it.timestamp), totalW) }, "Tuketim W", Color.parseColor("#1976D2"))
        )
    }

    private fun updateChart(chart: LineChart, vararg sets: LineDataSet) {
        chart.data = LineData(*sets)
        chart.data.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    // ── DB modları ────────────────────────────────────────────────────────

    private fun loadDbData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val hours = selectedMode.toIntOrNull() ?: 24
            val since = System.currentTimeMillis() - hours * 3_600_000L
            val readings: List<SensorReading> = if (hours <= 24)
                vm.get15MinBuckets(since)
            else
                vm.get6HourBuckets(since)

            if (readings.isEmpty()) { clearCharts(); return@launch }

            val t0 = readings.first().timestamp
            fun toX(ts: Long) = ((ts - t0) / 60_000f)  // dakika

            updateChart(
                binding.chartTempHum,
                lineSet(readings.map { Entry(toX(it.timestamp), it.temperatureC) },    "Sicaklik °C", Color.parseColor("#E65100")),
                lineSet(readings.map { Entry(toX(it.timestamp), it.humidityPercent) }, "Nem %",       Color.parseColor("#1565C0"))
            )
            updateChart(
                binding.chartSoilLight,
                lineSet(readings.map { Entry(toX(it.timestamp), it.soilMoisturePercent) }, "Toprak %", Color.parseColor("#2E7D32")),
                lineSet(readings.map { Entry(toX(it.timestamp), it.luxValue / 10f) },      "Isik /10", Color.parseColor("#F9A825"))
            )
            updateChart(
                binding.chartSolar,
                lineSet(readings.map { Entry(toX(it.timestamp), it.solarPowerW) }, "Solar W", Color.parseColor("#F9A825"))
            )
            val state  = vm.actuatorState.value
            val totalW = EnergyEstimator.fanWatts(state.fanDuty) +
                         EnergyEstimator.ledWatts(state.ledDuty) +
                         EnergyEstimator.pumpWatts(state.pumpDuty)
            updateChart(
                binding.chartConsumption,
                lineSet(readings.map { Entry(toX(it.timestamp), totalW) }, "Tuketim W", Color.parseColor("#1976D2"))
            )
        }
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────

    private fun setupCharts() {
        listOf(binding.chartTempHum, binding.chartSoilLight,
               binding.chartSolar,  binding.chartConsumption).forEach { chart ->
            chart.apply {
                description.isEnabled  = false
                legend.isEnabled       = true
                setTouchEnabled(true)
                isDragEnabled          = true
                setScaleEnabled(true)
                xAxis.position         = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.labelCount       = 5
                axisRight.isEnabled    = false
                axisLeft.gridColor     = Color.parseColor("#22000000")
                setNoDataText("Veri bekleniyor...")
            }
        }
    }

    private fun clearCharts() {
        listOf(binding.chartTempHum, binding.chartSoilLight,
               binding.chartSolar,  binding.chartConsumption).forEach {
            it.clear(); it.invalidate()
        }
    }

    private fun lineSet(entries: List<Entry>, label: String, color: Int): LineDataSet =
        LineDataSet(entries, label).apply {
            this.color = color
            setDrawCircles(entries.size <= 3)
            circleRadius = 3f
            setCircleColor(color)
            setDrawValues(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.LINEAR
        }

    fun refreshFromArgs() { if (selectedMode == "LIVE") renderLive() else loadDbData() }

    override fun onDestroyView() {
        super.onDestroyView()
        liveJob?.cancel()
        _binding = null
    }
}

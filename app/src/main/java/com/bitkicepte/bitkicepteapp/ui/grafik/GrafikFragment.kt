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
import com.bitkicepte.bitkicepteapp.ui.shared.SharedViewModel
import com.bitkicepte.bitkicepteapp.domain.engine.EnergyEstimator
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

import kotlinx.coroutines.launch

class GrafikFragment : Fragment() {

    private var _binding: FragmentGrafikBinding? = null
    private val binding get() = _binding!!
    private val vm: SharedViewModel by activityViewModels()

    // Secili aralik: 1=1saat, 24=24saat, 168=7gun (saat cinsinden)
    private var selectedHours = 1

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentGrafikBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCharts()
        setupTimeFilter()
        loadData()
    }

    private fun setupTimeFilter() {
        binding.toggleTime.check(R.id.btn1h)
        binding.toggleTime.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedHours = when (checkedId) {
                R.id.btn1h  -> 1
                R.id.btn24h -> 24
                R.id.btn7d  -> 168
                else -> 1
            }
            loadData()
        }
    }

    private fun setupCharts() {
        listOf(binding.chartTempHum, binding.chartSoilLight,
               binding.chartSolar, binding.chartConsumption).forEach { chart ->
            chart.apply {
                description.isEnabled = false
                legend.isEnabled = true
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.labelCount = 4
                axisRight.isEnabled = false
                axisLeft.gridColor = Color.parseColor("#22000000")
                setNoDataText("Veri bekleniyor...")
            }
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val since = now - selectedHours * 3_600_000L

            val readings: List<SensorReading> = when (selectedHours) {
                1    -> vm.getSensorSince(since)
                24   -> vm.get15MinBuckets(since)
                else -> vm.get6HourBuckets(since)
            }

            if (readings.isEmpty()) return@launch

            // X ekseni: dakika offseti (0 = en eski)
            val t0 = readings.first().timestamp.toFloat()

            fun toX(ts: Long) = (ts - t0) / 60_000f   // dakika

            // Grafik 1: Sicaklik + Nem
            val tempEntries = readings.map { Entry(toX(it.timestamp), it.temperatureC) }
            val humEntries  = readings.map { Entry(toX(it.timestamp), it.humidityPercent) }
            binding.chartTempHum.data = LineData(
                lineSet(tempEntries, "Sicaklik", Color.parseColor("#E65100")),
                lineSet(humEntries,  "Nem",      Color.parseColor("#1565C0"))
            )
            binding.chartTempHum.invalidate()

            // Grafik 2: Toprak + Isik (lux/10 ile olceklenir)
            val soilEntries  = readings.map { Entry(toX(it.timestamp), it.soilMoisturePercent) }
            val lightEntries = readings.map { Entry(toX(it.timestamp), it.luxValue / 10f) }
            binding.chartSoilLight.data = LineData(
                lineSet(soilEntries,  "Toprak %",   Color.parseColor("#2E7D32")),
                lineSet(lightEntries, "Isik /10",   Color.parseColor("#F9A825"))
            )
            binding.chartSoilLight.invalidate()

            // Grafik 3: Solar W
            val solarEntries = readings.map { Entry(toX(it.timestamp), it.solarPowerW) }
            binding.chartSolar.data = LineData(lineSet(solarEntries, "Solar W", Color.parseColor("#F9A825")))
            binding.chartSolar.invalidate()

            // Grafik 4: Tahmini tuketim (son aktuator durumundan)
            val state = vm.actuatorState.value
            val totalW = EnergyEstimator.fanWatts(state.fanDuty) +
                         EnergyEstimator.ledWatts(state.ledDuty) +
                         EnergyEstimator.pumpWatts(state.pumpDuty)
            val consEntries = readings.map { Entry(toX(it.timestamp), totalW) }
            binding.chartConsumption.data = LineData(lineSet(consEntries, "Tuketim W", Color.parseColor("#1976D2")))
            binding.chartConsumption.invalidate()
        }
    }

    private fun lineSet(entries: List<Entry>, label: String, color: Int): LineDataSet =
        LineDataSet(entries, label).apply {
            this.color = color
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(false)
        }

    /** MainActivity'den cagrilir - belirli sensor tipini on sece */
    fun refreshFromArgs() { loadData() }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

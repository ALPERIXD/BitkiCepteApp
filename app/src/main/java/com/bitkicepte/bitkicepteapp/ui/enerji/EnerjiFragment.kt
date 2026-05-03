package com.bitkicepte.bitkicepteapp.ui.enerji

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
import com.bitkicepte.bitkicepteapp.databinding.FragmentEnerjiBinding
import com.bitkicepte.bitkicepteapp.domain.engine.CarbonCalculator
import com.bitkicepte.bitkicepteapp.ui.shared.SharedViewModel
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

class EnerjiFragment : Fragment() {
    private var _b: FragmentEnerjiBinding? = null
    private val b get() = _b!!
    private val vm: SharedViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentEnerjiBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bCons  = b.kpiConsumption
        val bSolar = b.kpiSolar
        val bSave  = b.kpiSaving

        bCons.tvKpiLabel.text = "Bugun Tuketim"; bCons.tvKpiUnit.text = "Wh"
        bSolar.tvKpiLabel.text = "Solar Uretim"; bSolar.tvKpiUnit.text = "Wh"
        bSave.tvKpiLabel.text = "Net Tasarruf"; bSave.tvKpiUnit.text = "TL"

        setupBarChart()
        setupPieChart()
        setupResetButton()

        val dayStart = todayStartMs()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        vm.getTodayConsumptionWh(dayStart),
                        vm.getTodaySolarWh(dayStart)
                    ) { cons, solar -> Pair(cons, solar) }.collect { (cons, solar) ->
                        bCons.tvKpiValue.text = "%.0f".format(cons)
                        bSolar.tvKpiValue.text = "%.0f".format(solar)
                        val savedKwh = solar / 1000f
                        val tl = CarbonCalculator.kwhToCost(savedKwh)
                        bSave.tvKpiValue.text = "%.2f".format(tl)
                    }
                }
                launch {
                    vm.getLast7Days().collect { days ->
                        val reversed = days.reversed()
                        val dayLabels = Array(reversed.size) { i ->
                            val cal = Calendar.getInstance()
                            cal.set(Calendar.HOUR_OF_DAY, 0)
                            cal.add(Calendar.DAY_OF_YEAR, -(reversed.size - 1 - i))
                            listOf("Pzt","Sal","Car","Per","Cum","Cmt","Paz")[cal.get(Calendar.DAY_OF_WEEK) - 1]
                        }
                        val entries = reversed.mapIndexed { i, d ->
                            BarEntry(i.toFloat(), d.totalConsumptionKwh)
                        }
                        b.barChartWeek.data = BarData(BarDataSet(entries, "Tuketim kWh").apply {
                            color = Color.parseColor("#1976D2")
                            setDrawValues(false)
                        })
                        b.barChartWeek.xAxis.valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float) =
                                dayLabels.getOrElse(value.toInt()) { "" }
                        }
                        b.barChartWeek.invalidate()
                    }
                }
                launch {
                    val breakdown = vm.getTodayBreakdown(dayStart)
                    val total = breakdown.fanWh + breakdown.ledWh + breakdown.pumpWh
                    if (total > 0f) {
                        val entries = listOf(
                            PieEntry(breakdown.fanWh, "Fan"),
                            PieEntry(breakdown.ledWh, "LED"),
                            PieEntry(breakdown.pumpWh, "Pompa")
                        ).filter { it.value > 0f }
                        val ds = PieDataSet(entries, "").apply {
                            colors = listOf(
                                Color.parseColor("#1565C0"),
                                Color.parseColor("#F9A825"),
                                Color.parseColor("#2E7D32")
                            )
                            setDrawValues(true)
                            valueTextSize = 11f
                        }
                        b.pieChart.data = PieData(ds)
                        b.pieChart.invalidate()
                    }
                }
            }
        }
    }

    private fun setupResetButton() {
        b.btnResetEnergy.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Verileri Sıfırla")
                .setMessage("Tüm sensör, enerji ve grafik verileri silinecek. Emin misin?")
                .setPositiveButton("Sıfırla") { _, _ -> vm.resetAllData() }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
    }

    private fun setupBarChart() {
        b.barChartWeek.apply {
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            axisRight.isEnabled = false
            setNoDataText("Veri bekleniyor...")
        }
    }

    private fun setupPieChart() {
        b.pieChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 40f
            setNoDataText("Veri bekleniyor...")
        }
    }

    private fun todayStartMs(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

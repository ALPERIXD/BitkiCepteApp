package com.bitkicepte.bitkicepteapp.ui.surdurulebilirlik

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bitkicepte.bitkicepteapp.databinding.FragmentSurdurulebilirlikBinding
import com.bitkicepte.bitkicepteapp.domain.engine.CarbonCalculator
import com.bitkicepte.bitkicepteapp.ui.shared.SharedViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

class SurdurulebilirlikFragment : Fragment() {
    private var _b: FragmentSurdurulebilirlikBinding? = null
    private val b get() = _b!!
    private val vm: SharedViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSurdurulebilirlikBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    val dayStart = todayStartMs()
                    vm.getTodaySolarWh(dayStart).collect { solarWh ->
                        val summary = CarbonCalculator.buildSummary(solarWh / 1000f)
                        b.tvCo2Value.text = "%.3f".format(summary.savedCo2Kg)
                        b.tvTreeValue.text = "%.4f".format(summary.treeEquivalent)
                        b.tvCostValue.text = "%.2f".format(summary.savedTl)
                        b.tvSolarKwh.text = "%.3f kWh".format(summary.savedKwh)
                    }
                }
                launch {
                    vm.getLast7Days().collect { days ->
                        val totalSavedKwh = days.sumOf { it.solarProductionKwh.toDouble() }.toFloat()
                        val weeklyCo2 = CarbonCalculator.solarSavedCo2(totalSavedKwh)
                        b.tvWeeklyCo2.text = "Bu hafta: %.2f kg CO₂ tasarruf".format(weeklyCo2)
                    }
                }
            }
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

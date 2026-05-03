package com.bitkicepte.bitkicepteapp.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.slider.Slider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bitkicepte.bitkicepteapp.R
import com.bitkicepte.bitkicepteapp.data.local.entity.ControlMode
import com.bitkicepte.bitkicepteapp.databinding.FragmentHomeBinding
import com.bitkicepte.bitkicepteapp.databinding.ItemActuatorRowBinding
import com.bitkicepte.bitkicepteapp.databinding.ItemSensorCardBinding
import com.bitkicepte.bitkicepteapp.domain.model.ActuatorState
import com.bitkicepte.bitkicepteapp.domain.model.SensorData
import com.bitkicepte.bitkicepteapp.ui.shared.SharedViewModel
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm: SharedViewModel by activityViewModels()

    private lateinit var bTemp:  ItemSensorCardBinding
    private lateinit var bHum:   ItemSensorCardBinding
    private lateinit var bSoil:  ItemSensorCardBinding
    private lateinit var bLight: ItemSensorCardBinding
    private lateinit var bFan:   ItemActuatorRowBinding
    private lateinit var bLed:   ItemActuatorRowBinding
    private lateinit var bPump:  ItemActuatorRowBinding

    private var suppressActuator = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bTemp  = binding.cardTemp
        bHum   = binding.cardHum
        bSoil  = binding.cardSoil
        bLight = binding.cardLight
        bFan   = binding.rowFan
        bLed   = binding.rowLed
        bPump  = binding.rowPump

        bTemp.tvSensorLabel.text  = "Sicaklik";    bTemp.tvSensorUnit.text  = "C"
        bHum.tvSensorLabel.text   = "Nem";          bHum.tvSensorUnit.text   = "%"
        bSoil.tvSensorLabel.text  = "Toprak Nemi"; bSoil.tvSensorUnit.text  = "%"
        bLight.tvSensorLabel.text = "Isik";         bLight.tvSensorUnit.text = "lux"

        // İkonlar ve arka plan renkleri
        bTemp.ivSensorIcon.setImageResource(R.drawable.ic_thermometer)
        bTemp.flIconBox.setBackgroundResource(R.drawable.bg_icon_box_orange)

        bHum.ivSensorIcon.setImageResource(R.drawable.ic_humidity)
        bHum.flIconBox.setBackgroundResource(R.drawable.bg_icon_box_blue)

        bSoil.ivSensorIcon.setImageResource(R.drawable.ic_soil)
        bSoil.flIconBox.setBackgroundResource(R.drawable.bg_icon_box_green)

        bLight.ivSensorIcon.setImageResource(R.drawable.ic_light)
        bLight.flIconBox.setBackgroundResource(R.drawable.bg_icon_box_orange)

        bFan.tvActuatorLabel.text  = "Fan (PWM)"
        bLed.tvActuatorLabel.text  = "LED Grow (PWM)"
        bPump.tvActuatorLabel.text = "Su Pompasi (PWM)"

        bFan.ivActuatorIcon.setImageResource(R.drawable.ic_fan)
        bLed.ivActuatorIcon.setImageResource(R.drawable.ic_led)
        bPump.ivActuatorIcon.setImageResource(R.drawable.ic_pump)

        setupConnect()
        setupModeToggle()
        setupActuators()
        observeState()
    }

    private fun setupConnect() {
        binding.btnConnect.setOnClickListener {
            if (vm.connected.value) {
                vm.disconnect()
            } else {
                showConnectDialog()
            }
        }
    }

    private fun showConnectDialog() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("BitkiCepte", Context.MODE_PRIVATE)
        val savedIp   = prefs.getString("esp32_ip",   "192.168.1.100") ?: "192.168.1.100"
        val savedPort = prefs.getInt("esp32_port", 8080)

        val etIp   = EditText(ctx).apply { setText(savedIp);              hint = "ESP32 IP" }
        val etPort = EditText(ctx).apply { setText(savedPort.toString()); hint = "Port" }

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(etIp); addView(etPort)
        }

        AlertDialog.Builder(ctx)
            .setTitle("ESP32'ye Baglan")
            .setView(layout)
            .setPositiveButton("Baglan") { _, _ ->
                val ip   = etIp.text.toString().trim()
                val port = etPort.text.toString().toIntOrNull() ?: 8080
                prefs.edit().putString("esp32_ip", ip).putInt("esp32_port", port).apply()
                vm.connect(ip, port)
            }
            .setNegativeButton("Iptal", null)
            .show()
    }

    private fun setupModeToggle() {
        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.btnManual -> ControlMode.MANUAL
                R.id.btnAuto   -> ControlMode.AUTO
                R.id.btnSmart  -> ControlMode.SMART
                else -> return@addOnButtonCheckedListener
            }
            vm.setMode(mode)
            setActuatorEnabled(mode == ControlMode.MANUAL)
            binding.tvSmartReason.visibility =
                if (mode == ControlMode.SMART || mode == ControlMode.AUTO) View.VISIBLE else View.GONE
        }
    }

    private fun setActuatorEnabled(enabled: Boolean) {
        listOf(bFan, bLed, bPump).forEach {
            it.sliderActuator.isEnabled  = enabled
            it.switchActuator.isEnabled  = enabled
        }
    }

    private fun setupActuators() {
        listOf(bFan, bLed, bPump).forEach { row ->
            row.sliderActuator.addOnChangeListener { _, value, fromUser ->
                row.tvActuatorDuty.text = "${value.toInt()}%"
                if (fromUser) row.switchActuator.isChecked = value > 0
                if (fromUser && !suppressActuator) sendActuatorCommand()
            }
            row.sliderActuator.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}
                override fun onStopTrackingTouch(slider: Slider) { sendActuatorCommand() }
            })
            row.switchActuator.setOnCheckedChangeListener { _, checked ->
                if (!suppressActuator) {
                    if (!checked) row.sliderActuator.value = 0f
                    else if (row.sliderActuator.value == 0f) row.sliderActuator.value = 50f
                    sendActuatorCommand()
                }
            }
        }
    }

    private fun sendActuatorCommand() {
        if (vm.controlMode.value != ControlMode.MANUAL) return
        vm.setActuator(ActuatorState(
            fanDuty     = bFan.sliderActuator.value.toInt(),
            ledDuty     = bLed.sliderActuator.value.toInt(),
            pumpDuty    = bPump.sliderActuator.value.toInt(),
            controlMode = ControlMode.MANUAL
        ))
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    vm.connected.collect { connected ->
                        binding.tvConnectionStatus.text = if (connected) "Bagli" else "Bagli degil"
                        binding.tvConnectionStatus.setTextColor(
                            requireContext().getColor(if (connected) R.color.primary_green else R.color.accent_red)
                        )
                        binding.btnConnect.text = if (connected) "Kes" else "Baglan"
                    }
                }

                launch {
                    vm.latestSensor.collect { data ->
                        data ?: return@collect
                        updateSensorCards(data)
                        vm.onNewSensorData(data)
                    }
                }

                launch {
                    vm.actuatorState.collect { state ->
                        suppressActuator = true
                        requireView().post {
                            bFan.sliderActuator.value      = state.fanDuty.toFloat()
                            bFan.switchActuator.isChecked  = state.fanOn
                            bLed.sliderActuator.value      = state.ledDuty.toFloat()
                            bLed.switchActuator.isChecked  = state.ledOn
                            bPump.sliderActuator.value     = state.pumpDuty.toFloat()
                            bPump.switchActuator.isChecked = state.pumpOn
                            suppressActuator = false
                        }
                    }
                }

                launch {
                    vm.controlMode.collect { mode ->
                        val btnId = when (mode) {
                            ControlMode.MANUAL -> R.id.btnManual
                            ControlMode.AUTO   -> R.id.btnAuto
                            ControlMode.SMART  -> R.id.btnSmart
                        }
                        if (binding.toggleMode.checkedButtonId != btnId) {
                            binding.toggleMode.check(btnId)
                        }
                    }
                }

                launch {
                    vm.selectedProfile.collect { profile ->
                        binding.tvPlantProfile.text = if (profile != null)
                            "Bitki: ${profile.name}  |  Hedef DLI: ${profile.targetDli} mol/m²  |  VPD: ${profile.vpdMin}-${profile.vpdMax} kPa"
                        else
                            "Profil secilmedi — Ayarlar > Bitki Profili"
                    }
                }

                launch {
                    vm.smartReason.collect { reason ->
                        val mode = vm.controlMode.value
                        if ((mode == ControlMode.SMART || mode == ControlMode.AUTO) && reason.isNotEmpty()) {
                            binding.tvSmartReason.text = reason
                            binding.tvSmartReason.visibility = View.VISIBLE
                        } else if (mode == ControlMode.MANUAL) {
                            binding.tvSmartReason.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun updateSensorCards(d: SensorData) {
        bTemp.tvSensorValue.text  = "%.1f".format(d.temperatureC)
        bHum.tvSensorValue.text   = "%.1f".format(d.humidityPercent)
        bSoil.tvSensorValue.text  = "%.0f".format(d.soilMoisturePercent)
        bLight.tvSensorValue.text = "%.0f".format(d.luxValue)
        binding.tvSolarPower.text  = "%.1f W".format(d.solarPowerW)
        binding.tvSolarDetail.text = "  %.1fV / %.2fA".format(d.solarVoltage, d.solarCurrentA)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

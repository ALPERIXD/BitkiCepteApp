package com.bitkicepte.bitkicepteapp.ui.ayarlar

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bitkicepte.bitkicepteapp.data.local.entity.PlantProfile
import com.bitkicepte.bitkicepteapp.databinding.FragmentAyarlarBinding
import com.bitkicepte.bitkicepteapp.ui.shared.SharedViewModel
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class AyarlarFragment : Fragment() {
    private var _b: FragmentAyarlarBinding? = null
    private val b get() = _b!!
    private val vm: SharedViewModel by activityViewModels()

    private var profileList: List<PlantProfile> = emptyList()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentAyarlarBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = requireContext().getSharedPreferences("BitkiCepte", Context.MODE_PRIVATE)

        // Kayıtlı değerleri yükle
        b.etIp.setText(prefs.getString("esp32_ip", "192.168.1.100"))
        b.etPort.setText(prefs.getInt("esp32_port", 8080).toString())
        b.etTempMin.setText(prefs.getFloat("temp_min", 18f).toString())
        b.etTempMax.setText(prefs.getFloat("temp_max", 28f).toString())
        b.etHumMax.setText(prefs.getFloat("hum_max", 80f).toString())
        b.etSoilMin.setText(prefs.getFloat("soil_min", 30f).toString())
        b.etPrice.setText(prefs.getFloat("price_tl", 4.60f).toString())
        b.etInstallCost.setText(prefs.getFloat("install_cost_tl", 5000f).toString())

        // Profil listesi
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.allProfiles.collect { profiles ->
                    profileList = profiles
                    val names = profiles.map {
                        if (it.isCustom) "★ ${it.name}" else it.name
                    }
                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        names
                    ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                    b.spinnerPlant.adapter = adapter

                    val selectedIdx = profiles.indexOfFirst { it.id == vm.selectedProfile.value?.id }
                    if (selectedIdx >= 0) b.spinnerPlant.setSelection(selectedIdx)

                    updateProfileDetail(vm.selectedProfile.value)
                }
            }
        }

        b.spinnerPlant.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val profile = profileList.getOrNull(pos)
                updateProfileDetail(profile)
                b.btnDeleteProfile.isEnabled = profile?.isCustom == true
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        b.btnApplyProfile.setOnClickListener {
            val profile = profileList.getOrNull(b.spinnerPlant.selectedItemPosition) ?: return@setOnClickListener
            vm.selectProfile(profile)
            b.etTempMin.setText(profile.tempMinC.toString())
            b.etTempMax.setText(profile.tempMaxC.toString())
            b.etSoilMin.setText(profile.soilMinPercent.toString())
            Toast.makeText(requireContext(), "${profile.name} profili uygulandı", Toast.LENGTH_SHORT).show()
        }

        b.btnAddProfile.setOnClickListener { showAddProfileDialog() }

        b.btnDeleteProfile.setOnClickListener {
            val profile = profileList.getOrNull(b.spinnerPlant.selectedItemPosition) ?: return@setOnClickListener
            if (!profile.isCustom) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("Profili Sil")
                .setMessage("\"${profile.name}\" silinsin mi?")
                .setPositiveButton("Sil") { _, _ ->
                    vm.deleteCustomProfile(profile.id)
                    Toast.makeText(requireContext(), "${profile.name} silindi", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Vazgeç", null)
                .show()
        }

        b.btnSave.setOnClickListener {
            prefs.edit().apply {
                putString("esp32_ip", b.etIp.text.toString().trim())
                putInt("esp32_port", b.etPort.text.toString().toIntOrNull() ?: 8080)
                putFloat("temp_min", b.etTempMin.text.toString().toFloatOrNull() ?: 18f)
                putFloat("temp_max", b.etTempMax.text.toString().toFloatOrNull() ?: 28f)
                putFloat("hum_max", b.etHumMax.text.toString().toFloatOrNull() ?: 80f)
                putFloat("soil_min", b.etSoilMin.text.toString().toFloatOrNull() ?: 30f)
                putFloat("price_tl", b.etPrice.text.toString().toFloatOrNull() ?: 4.60f)
                putFloat("install_cost_tl", b.etInstallCost.text.toString().toFloatOrNull() ?: 5000f)
            }.apply()
            Toast.makeText(requireContext(), "Kaydedildi", Toast.LENGTH_SHORT).show()
        }

        b.btnConnect.setOnClickListener {
            val ip = b.etIp.text.toString().trim()
            val port = b.etPort.text.toString().toIntOrNull() ?: 8080
            vm.connect(ip, port)
            Toast.makeText(requireContext(), "Baglaniliyor: $ip:$port", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddProfileDialog() {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val pad = (dp * 24).toInt()

        fun makeField(hint: String, default: String, numericDecimal: Boolean = true): Pair<TextInputLayout, TextInputEditText> {
            val et = TextInputEditText(ctx).apply {
                setText(default)
                inputType = if (numericDecimal)
                    android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                else android.text.InputType.TYPE_CLASS_TEXT
            }
            val til = TextInputLayout(ctx).apply {
                this.hint = hint
                addView(et)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (dp * 4).toInt()
                layoutParams = lp
            }
            return til to et
        }

        val (tilName,   etName)   = makeField("Profil Adı",         "",     false)
        val (tilDli,    etDli)    = makeField("Hedef DLI (mol/m²)", "15.0")
        val (tilVpdMin, etVpdMin) = makeField("VPD Min (kPa)",       "0.8")
        val (tilVpdMax, etVpdMax) = makeField("VPD Max (kPa)",       "1.5")
        val (tilSoil,   etSoil)   = makeField("Toprak Min (%)",      "35")
        val (tilTmpMin, etTmpMin) = makeField("Sıcaklık Min (°C)",  "15.0")
        val (tilTmpMax, etTmpMax) = makeField("Sıcaklık Max (°C)",  "28.0")

        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (dp * 8).toInt(), pad, (dp * 8).toInt())
            listOf(tilName, tilDli, tilVpdMin, tilVpdMax, tilSoil, tilTmpMin, tilTmpMax)
                .forEach { addView(it) }
        }

        AlertDialog.Builder(ctx)
            .setTitle("Yeni Bitki Profili")
            .setView(ScrollView(ctx).apply { addView(inner) })
            .setPositiveButton("Ekle") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(ctx, "Profil adı boş olamaz", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                vm.addCustomProfile(PlantProfile(
                    name           = name,
                    targetDli      = etDli.text.toString().toFloatOrNull()    ?: 15f,
                    vpdMin         = etVpdMin.text.toString().toFloatOrNull() ?: 0.8f,
                    vpdMax         = etVpdMax.text.toString().toFloatOrNull() ?: 1.5f,
                    soilMinPercent = etSoil.text.toString().toFloatOrNull()   ?: 35f,
                    tempMinC       = etTmpMin.text.toString().toFloatOrNull() ?: 15f,
                    tempMaxC       = etTmpMax.text.toString().toFloatOrNull() ?: 28f,
                    isCustom       = true
                ))
                Toast.makeText(ctx, "$name eklendi ve seçildi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun updateProfileDetail(profile: PlantProfile?) {
        if (profile == null) { b.tvProfileDetail.text = ""; return }
        b.tvProfileDetail.text =
            "DLI: ${profile.targetDli} mol/m²/g  |  " +
            "VPD: ${profile.vpdMin}-${profile.vpdMax} kPa  |  " +
            "Toprak min: %${profile.soilMinPercent.toInt()}  |  " +
            "Sicaklik: ${profile.tempMinC}-${profile.tempMaxC}°C"
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

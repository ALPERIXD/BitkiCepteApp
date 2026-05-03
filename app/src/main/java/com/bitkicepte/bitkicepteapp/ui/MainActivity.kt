package com.bitkicepte.bitkicepteapp.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.bitkicepte.bitkicepteapp.R
import com.bitkicepte.bitkicepteapp.databinding.ActivityMainBinding
import com.bitkicepte.bitkicepteapp.ui.shared.SharedViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    val sharedViewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: içerik status bar + nav bar alanını kaplasın
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fragment alanı üstten status bar kadar padding alsın
        // Bottom nav altta nav gesture bar kadar padding alsın
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navHostFragment.updatePadding(top = bars.top)
            binding.bottomNav.updatePadding(bottom = bars.bottom)
            insets
        }

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.bottomNav.setupWithNavController(navHost.navController)
    }
}

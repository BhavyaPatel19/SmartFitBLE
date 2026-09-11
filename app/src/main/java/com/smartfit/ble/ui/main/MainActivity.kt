package com.smartfit.ble.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.smartfit.ble.R
import com.smartfit.ble.SmartFitApp
import com.smartfit.ble.data.SessionManager
import com.smartfit.ble.databinding.ActivityMainBinding
import com.smartfit.ble.ui.login.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val sessionManager by lazy { SessionManager(applicationContext) }

    // ✅ GET REPOSITORY FROM APP CONTAINER
    private val healthRepository by lazy {
        val app = application as SmartFitApp
        app.appContainer.healthRepository
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ SESSION CHECK + BACKUP RESTORE
        lifecycleScope.launch(Dispatchers.IO) {
            val session = sessionManager.sessionFlow.firstOrNull()
            if (session == null) {
                withContext(Dispatchers.Main) {
                    redirectToLogin()
                }
                return@launch
            }

            // 🔄 RESTORE BACKUP FROM FIREBASE (on background thread)
            healthRepository.restoreFromFirebaseIfNeeded()
        }

        setupNavigation()
    }

    private fun setupNavigation() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.dashboardFragment,
                R.id.historyFragment,
                R.id.devicesFragment,
                R.id.graphsFragment,
                R.id.informationFragment
            )
        )

        binding.topAppBar.setupWithNavController(navController, appBarConfiguration)
        binding.bottomNav.setupWithNavController(navController)

        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_ble -> {
                    if (navController.currentDestination?.id != R.id.devicesFragment) {
                        navController.navigate(R.id.devicesFragment)
                    }
                    true
                }
                R.id.menu_profile -> {
                    if (navController.currentDestination?.id != R.id.profileFragment) {
                        navController.navigate(R.id.profileFragment)
                    }
                    true
                }
                else -> false
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isProfileFragment = destination.id == R.id.profileFragment
            val isDevicesFragment = destination.id == R.id.devicesFragment

            binding.topAppBar.menu.findItem(R.id.menu_profile)?.isVisible = !isProfileFragment
            binding.topAppBar.menu.findItem(R.id.menu_ble)?.isVisible = !isProfileFragment

            binding.bottomNav.visibility =
                if (isProfileFragment) View.GONE else View.VISIBLE

            val showBackButton = isProfileFragment || isDevicesFragment
            binding.topAppBar.navigationIcon =
                if (showBackButton) getDrawable(R.drawable.ic_arrow_back) else null

            if (showBackButton) {
                binding.topAppBar.setNavigationOnClickListener {
                    if (!navController.popBackStack()) {
                        navController.navigate(R.id.dashboardFragment)
                    }
                }
            } else {
                binding.topAppBar.setNavigationOnClickListener(null)
            }
        }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

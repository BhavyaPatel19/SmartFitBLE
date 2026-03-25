package com.smartfit.ble

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.smartfit.ble.data.UserPreferences
import com.smartfit.ble.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmartFitApp : Application() {

    private val userPreferences by lazy { UserPreferences(applicationContext) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        appContainer = AppContainer(this)

        // 🔥 Continuously observe settings
        applicationScope.launch {
            userPreferences.settingsFlow.collect { settings ->

                val nightMode = if (settings.isDarkMode) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
                AppCompatDelegate.setDefaultNightMode(nightMode)

                val localeTag = if (settings.useHindi) "hi" else "en"
                val locales = LocaleListCompat.forLanguageTags(localeTag)
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }
    }
}
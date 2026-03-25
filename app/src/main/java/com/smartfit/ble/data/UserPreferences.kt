package com.smartfit.ble.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.uiPreferencesDataStore by preferencesDataStore(
    name = "smartfit_ui_preferences"
)

data class UiSettings(
    val isDarkMode: Boolean,
    val useHindi: Boolean
)

class UserPreferences(private val context: Context) {

    private val dataStore = context.uiPreferencesDataStore

    val settingsFlow: Flow<UiSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            UiSettings(
                isDarkMode = preferences[DARK_MODE_ENABLED] ?: false,
                useHindi = preferences[USE_HINDI_LOCALE] ?: false
            )
        }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[DARK_MODE_ENABLED] = enabled
        }
    }

    suspend fun setHindiLocale(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[USE_HINDI_LOCALE] = enabled
        }
    }

    private companion object {
        val DARK_MODE_ENABLED = booleanPreferencesKey("dark_mode_enabled")
        val USE_HINDI_LOCALE = booleanPreferencesKey("use_hindi_locale")
    }
}


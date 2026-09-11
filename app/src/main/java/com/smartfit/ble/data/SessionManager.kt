package com.smartfit.ble.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartfit.shared.auth.LoginResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.sessionDataStore by preferencesDataStore(
    name = "smartfit_session"
)

data class AuthSession(
    val id: Int,
    val firebaseUid: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val imageUrl: String,
    val accessToken: String,
    val refreshToken: String,
    val loginType: LoginType
) {
    // ✅ REQUIRED BY ProfileFragment
    val fullName: String
        get() = listOf(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")

    val initials: String
        get() = buildString {
            if (firstName.isNotBlank()) append(firstName.first())
            if (lastName.isNotBlank()) append(lastName.first())
        }.uppercase()
}

class SessionManager(private val context: Context) {

    private val dataStore = context.sessionDataStore

    val sessionFlow: Flow<AuthSession?> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            val accessToken = prefs[ACCESS_TOKEN] ?: return@map null
            val refreshToken = prefs[REFRESH_TOKEN] ?: return@map null

            AuthSession(
                id = prefs[USER_ID]?.toIntOrNull() ?: -1,
                firebaseUid = prefs[FIREBASE_UID].orEmpty(),
                username = prefs[USERNAME].orEmpty(),
                firstName = prefs[FIRST_NAME].orEmpty(),
                lastName = prefs[LAST_NAME].orEmpty(),
                email = prefs[EMAIL].orEmpty(),
                imageUrl = prefs[IMAGE_URL].orEmpty(),
                accessToken = accessToken,
                refreshToken = refreshToken,
                loginType = try {
                    prefs[LOGIN_TYPE]?.let { LoginType.valueOf(it) } ?: LoginType.API
                } catch (e: IllegalArgumentException) {
                    LoginType.API
                }
            )
        }

    /** API LOGIN */
    suspend fun saveSession(response: LoginResponse) {
        val firebaseUid = response.username

        dataStore.edit { prefs ->
            prefs[USER_ID] = response.id.toString()
            prefs[FIREBASE_UID] = firebaseUid
            prefs[USERNAME] = response.username
            prefs[FIRST_NAME] = response.firstName
            prefs[LAST_NAME] = response.lastName
            prefs[EMAIL] = response.email
            prefs[IMAGE_URL] = response.image
            prefs[ACCESS_TOKEN] = response.accessToken
            prefs[REFRESH_TOKEN] = response.refreshToken
            prefs[LOGIN_TYPE] = LoginType.API.name
        }
    }


    /** GOOGLE LOGIN */
    suspend fun saveGoogleSession(session: AuthSession) {
        dataStore.edit { prefs ->
            prefs[USER_ID] = session.id.toString()
            prefs[FIREBASE_UID] = session.firebaseUid
            prefs[USERNAME] = session.username
            prefs[FIRST_NAME] = session.firstName
            prefs[LAST_NAME] = session.lastName
            prefs[EMAIL] = session.email
            prefs[IMAGE_URL] = session.imageUrl
            prefs[ACCESS_TOKEN] = session.accessToken
            prefs[REFRESH_TOKEN] = session.refreshToken
            prefs[LOGIN_TYPE] = LoginType.GOOGLE.name
        }
    }

    suspend fun clearSession() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val USER_ID = stringPreferencesKey("user_id")
        val FIREBASE_UID = stringPreferencesKey("firebase_uid")
        val USERNAME = stringPreferencesKey("username")
        val FIRST_NAME = stringPreferencesKey("first_name")
        val LAST_NAME = stringPreferencesKey("last_name")
        val EMAIL = stringPreferencesKey("email")
        val IMAGE_URL = stringPreferencesKey("image_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val LOGIN_TYPE = stringPreferencesKey("login_type")
    }
}

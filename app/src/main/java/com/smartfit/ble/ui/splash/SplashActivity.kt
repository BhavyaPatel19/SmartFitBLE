package com.smartfit.ble.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.smartfit.ble.R
import com.smartfit.ble.data.LoginType
import com.smartfit.ble.data.SessionManager
import com.smartfit.ble.ui.login.LoginActivity
import com.smartfit.ble.ui.main.MainActivity
import com.smartfit.shared.auth.AuthClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : ComponentActivity() {

    private val sessionManager by lazy { SessionManager(applicationContext) }
    private val authClient = AuthClient()
    private var keepOnScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        splashScreen.setKeepOnScreenCondition { keepOnScreen }
        checkSessionAndNavigate()
    }

    private fun checkSessionAndNavigate() {
        lifecycleScope.launch {
            val session = sessionManager.sessionFlow.firstOrNull()
            if (session == null) {
                keepOnScreen = false
                navigateToLogin()
                return@launch
            }

            val isValid = validateSession(session)
            keepOnScreen = false
            
            if (isValid) {
                navigateToMain()
            } else {
                navigateToLogin()
            }
        }
    }

    private suspend fun validateSession(session: com.smartfit.ble.data.AuthSession): Boolean {
        return when (session.loginType) {
            LoginType.API -> withContext(Dispatchers.IO) {
                try {
                    authClient.me(session.accessToken)
                    true
                } catch (_: Exception) {
                    sessionManager.clearSession()
                    false
                }
            }

            LoginType.GOOGLE, LoginType.EMAIL -> {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    sessionManager.clearSession()
                    false
                } else {
                    true
                }
            }
        }
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

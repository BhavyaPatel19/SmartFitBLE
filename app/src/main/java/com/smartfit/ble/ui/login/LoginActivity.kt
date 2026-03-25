package com.smartfit.ble.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.smartfit.ble.R
import com.smartfit.ble.SmartFitApp
import com.smartfit.ble.data.AuthSession
import com.smartfit.ble.data.LoginType
import com.smartfit.ble.data.SessionManager
import com.smartfit.ble.data.firebase.FirebaseUserDataSource
import com.smartfit.ble.data.model.UserProfile
import com.smartfit.ble.databinding.ActivityLoginBinding
import com.smartfit.ble.ui.main.MainActivity
import com.smartfit.ble.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class LoginActivity : ComponentActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val sessionManager by lazy {
        SessionManager(applicationContext)
    }

    private val firebaseUserDataSource by lazy {
        FirebaseUserDataSource()
    }

    // ✅ REQUIRED: access HealthRepository to clear local Room data
    private val healthRepository by lazy {
        (application as SmartFitApp).appContainer.healthRepository
    }

    /* ---------------- LIFECYCLE ---------------- */

    private enum class AuthMode { LOGIN, SIGNUP }
    private var authMode: AuthMode = AuthMode.LOGIN

    private val doctorEmail = "doctor@clinic.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start in login mode (users can choose to create account)
        setAuthMode(AuthMode.LOGIN)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            when (authMode) {
                AuthMode.LOGIN -> attemptEmailLogin()
                AuthMode.SIGNUP -> attemptEmailSignUp()
            }
        }

        binding.tvToggleAuthMode.setOnClickListener {
            setAuthMode(if (authMode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN)
        }

    }

    private fun setAuthMode(mode: AuthMode) {
        authMode = mode
        val isSignUp = mode == AuthMode.SIGNUP

        binding.formTitle.text = getString(if (isSignUp) R.string.create_account else R.string.login)
        binding.formSubtitle.text = getString(if (isSignUp) R.string.signup_message else R.string.login_message)

        binding.nameInputLayout.isVisible = isSignUp
        binding.nameEditText.isEnabled = isSignUp
        binding.nameEditText.text?.clear()

        binding.loginButton.text = getString(if (isSignUp) R.string.create_account else R.string.login)
        binding.tvToggleAuthMode.text = getString(if (isSignUp) R.string.already_have_account else R.string.create_account)
    }

    /* ---------------- EMAIL/PASSWORD LOGIN ---------------- */

    private fun attemptEmailLogin() {
        val email = binding.emailEditText.text?.toString()?.trim().orEmpty()
        val password = binding.passwordEditText.text?.toString()?.trim().orEmpty()

        if (email.isBlank() || password.length < 6) {
            ToastUtil.showError(this, getString(R.string.error_invalid_credentials))
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                val auth = FirebaseAuth.getInstance()
                val firebaseUser = auth
                    .signInWithEmailAndPassword(email, password)
                    .await()
                    .user ?: throw IllegalStateException("Firebase user null")

                // Create / update Firebase profile
                val firebaseUid = firebaseUser.uid

                val emailValue = firebaseUser.email.orEmpty()
                val fallbackDisplayName = emailValue.substringBefore("@").ifBlank { "User" }
                val remoteDisplayName = firebaseUserDataSource.getDisplayName(firebaseUid)?.trim().orEmpty()

                val displayName = when {
                    emailValue.equals(doctorEmail, ignoreCase = true) -> "Doctor"
                    remoteDisplayName.isBlank() -> fallbackDisplayName
                    remoteDisplayName.equals("Doctor", ignoreCase = true) -> fallbackDisplayName
                    else -> remoteDisplayName
                }

                // If old profile had "Doctor" for non-doctor user, fix it.
                if (!emailValue.equals(doctorEmail, ignoreCase = true) &&
                    remoteDisplayName.equals("Doctor", ignoreCase = true)
                ) {
                    firebaseUserDataSource.setDisplayName(firebaseUid, displayName)
                }

                firebaseUserDataSource.createUserIfNotExists(
                    UserProfile(
                        uid = firebaseUid,
                        email = firebaseUser.email.orEmpty(),
                        displayName = displayName,
                        loginType = LoginType.EMAIL.name
                    )
                )

                val firstName = displayName.substringBefore(" ").trim().ifBlank { displayName }
                val lastName = displayName.substringAfter(" ", "").trim()

                // Save Firebase session
                sessionManager.saveFirebaseSession(
                    AuthSession(
                        id = firebaseUser.uid.hashCode(),
                        firebaseUid = firebaseUser.uid,
                        username = firebaseUser.email.orEmpty(),
                        firstName = firstName,
                        lastName = lastName,
                        email = firebaseUser.email.orEmpty(),
                        imageUrl = firebaseUser.photoUrl?.toString().orEmpty(),
                        accessToken = "EMAIL",
                        refreshToken = "EMAIL",
                        loginType = LoginType.EMAIL
                    )
                )

                healthRepository.clearLocalData()

                navigateToMain()
            } catch (e: Exception) {
                ToastUtil.showError(this@LoginActivity, getString(R.string.error_invalid_credentials))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun attemptEmailSignUp() {
        val fullName = binding.nameEditText.text?.toString()?.trim().orEmpty()
        val email = binding.emailEditText.text?.toString()?.trim().orEmpty()
        val password = binding.passwordEditText.text?.toString()?.trim().orEmpty()

        if (fullName.isBlank() || email.isBlank() || password.length < 6) {
            ToastUtil.showError(this, getString(R.string.error_invalid_credentials))
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                val auth = FirebaseAuth.getInstance()
                val firebaseUser = auth
                    .createUserWithEmailAndPassword(email, password)
                    .await()
                    .user ?: throw IllegalStateException("Firebase user null")

                val firebaseUid = firebaseUser.uid

                firebaseUserDataSource.createUserIfNotExists(
                    UserProfile(
                        uid = firebaseUid,
                        email = firebaseUser.email.orEmpty(),
                        displayName = fullName,
                        loginType = LoginType.EMAIL.name
                    )
                )

                val firstName = fullName.substringBefore(" ").trim().ifBlank { "User" }
                val lastName = fullName.substringAfter(" ", "").trim()

                sessionManager.saveFirebaseSession(
                    AuthSession(
                        id = firebaseUid.hashCode(),
                        firebaseUid = firebaseUid,
                        username = firebaseUser.email.orEmpty(),
                        firstName = firstName,
                        lastName = lastName,
                        email = firebaseUser.email.orEmpty(),
                        imageUrl = "",
                        accessToken = "EMAIL",
                        refreshToken = "EMAIL",
                        loginType = LoginType.EMAIL
                    )
                )

                healthRepository.clearLocalData()
                navigateToMain()
            } catch (e: Exception) {
                ToastUtil.showError(this@LoginActivity, getString(R.string.error_login_failed))
            } finally {
                setLoading(false)
            }
        }
    }

        /* ---------------- UI HELPERS ---------------- */

    private fun setLoading(loading: Boolean) {
        binding.progressIndicator.isVisible = loading
        binding.loginButton.isEnabled = !loading
    }

    private fun navigateToMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        finish()
    }
}   

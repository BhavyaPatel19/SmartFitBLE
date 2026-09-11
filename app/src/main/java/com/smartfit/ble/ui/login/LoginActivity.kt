package com.smartfit.ble.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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
import com.smartfit.shared.auth.AuthClient
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

    private val authClient by lazy {
        AuthClient()
    }

    // ✅ REQUIRED: access HealthRepository to clear local Room data
    private val healthRepository by lazy {
        (application as SmartFitApp).appContainer.healthRepository
    }

    /* ---------------- GOOGLE SIGN-IN ---------------- */

    private val googleClient by lazy {
        GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.google_web_client_id))
                .requestEmail()
                .build()
        )
    }

    private val googleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                runCatching { task.result }
                    .onSuccess { account ->
                        try {
                            firebaseLogin(account.idToken!!)
                        } catch (e: NullPointerException) {
                            ToastUtil.showError(this, "Google sign-in failed: missing token")
                        }
                    }
                    .onFailure {
                        ToastUtil.showError(this, "Google sign-in failed")
                    }
            }
        }

    /* ---------------- LIFECYCLE ---------------- */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.googleSignInButton.setSize(SignInButton.SIZE_WIDE)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener { attemptApiLogin() }
        binding.googleSignInButton.setOnClickListener {
            // 🔥 Force Google to forget previous account
            googleClient.signOut().addOnCompleteListener {
                googleLauncher.launch(googleClient.signInIntent)
            }
        }

    }

    /* ---------------- API LOGIN ---------------- */

    private fun attemptApiLogin() {
        val username = binding.emailEditText.text?.toString()?.trim().orEmpty()
        val password = binding.passwordEditText.text?.toString()?.trim().orEmpty()

        if (username.isBlank() || password.length < 4) {
            ToastUtil.showError(this, getString(R.string.error_invalid_credentials))
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                val response = withContext(Dispatchers.IO) {
                    authClient.login(username, password)
                }

                // Save API session locally
                sessionManager.saveSession(response)

                // 🔥 CRITICAL: Authenticate with Firebase
                FirebaseAuth.getInstance()
                    .signInAnonymously()
                    .await()

                healthRepository.clearLocalData()

                navigateToMain()

            } catch (e: Exception) {
                ToastUtil.showError(
                    this@LoginActivity,
                    getString(R.string.error_invalid_credentials)
                )
            } finally {
                setLoading(false)
            }
        }
    }

    /* ---------------- GOOGLE LOGIN ---------------- */

    private fun firebaseLogin(idToken: String) {
        lifecycleScope.launch {
            setLoading(true)
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val firebaseUser = FirebaseAuth.getInstance()
                    .signInWithCredential(credential)
                    .await()
                    .user ?: throw IllegalStateException("Firebase user null")

                // Create / update Firebase profile
                val firebaseUid = firebaseUser.uid

                firebaseUserDataSource.createUserIfNotExists(
                    UserProfile(
                        uid = firebaseUid,
                        email = firebaseUser.email.orEmpty(),
                        displayName = firebaseUser.displayName ?: "User",
                        loginType = LoginType.GOOGLE.name
                    )
                )



                // Save Google session
                sessionManager.saveGoogleSession(
                    AuthSession(
                        id = firebaseUser.uid.hashCode(),
                        firebaseUid = firebaseUser.uid,
                        username = firebaseUser.email.orEmpty(),
                        firstName = firebaseUser.displayName
                            ?.substringBefore(" ")
                            .orEmpty(),
                        lastName = firebaseUser.displayName
                            ?.substringAfter(" ", "")
                            .orEmpty(),
                        email = firebaseUser.email.orEmpty(),
                        imageUrl = firebaseUser.photoUrl?.toString().orEmpty(),
                        accessToken = "GOOGLE",
                        refreshToken = "GOOGLE",
                        loginType = LoginType.GOOGLE
                    )
                )


                healthRepository.clearLocalData()

                navigateToMain()
            } catch (e: Exception) {
                ToastUtil.showError(this@LoginActivity, "Google login failed")
            } finally {
                setLoading(false)
            }
        }
    }

        /* ---------------- UI HELPERS ---------------- */

    private fun setLoading(loading: Boolean) {
        binding.progressIndicator.isVisible = loading
        binding.loginButton.isEnabled = !loading
        binding.googleSignInButton.isEnabled = !loading
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

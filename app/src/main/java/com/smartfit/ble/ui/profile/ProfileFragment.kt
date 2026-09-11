package com.smartfit.ble.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.smartfit.ble.R
import com.smartfit.ble.data.SessionManager
import com.smartfit.ble.data.UserPreferences
import com.smartfit.ble.databinding.FragmentProfileBinding
import com.smartfit.ble.ui.login.LoginActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val sessionManager by lazy { SessionManager(requireContext().applicationContext) }
    private val userPreferences by lazy { UserPreferences(requireContext().applicationContext) }

    private val darkModeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewLifecycleOwner.lifecycleScope.launch {
            userPreferences.setDarkMode(isChecked)
        }
    }

    private val localizationListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewLifecycleOwner.lifecycleScope.launch {
            userPreferences.setHindiLocale(isChecked)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupClickListeners()
        loadUserSessionData()
        loadSettings()
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun loadUserSessionData() {
        viewLifecycleOwner.lifecycleScope.launch {
            sessionManager.sessionFlow.collectLatest { session ->
                if (session != null) {
                    updateUserProfile(session)
                }
            }
        }
    }

    private fun updateUserProfile(session: com.smartfit.ble.data.AuthSession) {
        binding.tvName.text = session.fullName
        binding.tvEmailValue.text = session.email
        binding.tvUsernameValue.text = "@${session.username}"

        val initials = session.initials.ifBlank {
            session.fullName
                .split(" ")
                .mapNotNull { it.firstOrNull()?.uppercase() }
                .joinToString("")
        }

        if (session.imageUrl.isNullOrEmpty()) {
            binding.ivProfileImage.visibility = View.GONE
            binding.tvInitials.visibility = View.VISIBLE
            binding.tvInitials.text = initials
        } else {
            binding.ivProfileImage.visibility = View.VISIBLE
            binding.tvInitials.visibility = View.GONE

            binding.ivProfileImage.load(session.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_avatar_circle)
                error(R.drawable.bg_avatar_circle)
            }
        }
    }

    private fun loadSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            userPreferences.settingsFlow.collectLatest { settings ->
                binding.switchDarkMode.setOnCheckedChangeListener(null)
                binding.switchLocalization.setOnCheckedChangeListener(null)

                binding.switchDarkMode.isChecked = settings.isDarkMode
                binding.switchLocalization.isChecked = settings.useHindi

                binding.switchDarkMode.setOnCheckedChangeListener(darkModeListener)
                binding.switchLocalization.setOnCheckedChangeListener(localizationListener)
            }
        }
    }

    private fun logout() {
        binding.btnLogout.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            sessionManager.clearSession()
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

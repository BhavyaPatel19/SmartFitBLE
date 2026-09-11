package com.smartfit.ble.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.smartfit.ble.R
import com.smartfit.ble.SmartFitApp
import com.smartfit.ble.data.SessionManager
import com.smartfit.ble.databinding.FragmentDashboardBinding
import com.smartfit.ble.ui.addentry.AddEntryDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    
    private val appContainer by lazy { (requireActivity().application as SmartFitApp).appContainer }
    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(appContainer.healthRepository)
    }
    
    private val sessionManager by lazy { SessionManager(requireContext().applicationContext) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        observeViewModel()
        loadUserGreeting()
        loadStats()
    }

    private fun setupClickListeners() {
        binding.fabAddEntry.setOnClickListener {
            showAddEntryDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.latestRecord.collectLatest { record ->
                updateDashboard(record)
            }
        }
    }

    private fun loadUserGreeting() {
        viewLifecycleOwner.lifecycleScope.launch {
            sessionManager.sessionFlow.collectLatest { session ->
                val greeting = if (session != null) {
                    getString(R.string.dashboard_greeting, session.firstName.ifBlank { "User" })
                } else {
                    getString(R.string.dashboard_greeting, "User")
                }
                binding.tvUserGreeting.text = greeting
            }
        }
    }

    private fun updateDashboard(record: com.smartfit.ble.data.model.HealthRecord?) {
        if (record != null) {
            binding.tvHeartRate.text = "${record.heartRate} ${getString(R.string.bpm)}"
            binding.tvBloodPressure.text = record.bloodPressure
            binding.cardVitals.visibility = View.VISIBLE
            
            // Show timestamp
            val formatter = java.text.SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", java.util.Locale.getDefault())
            binding.tvDataTimestamp.text = getString(R.string.last_updated, formatter.format(record.timestamp))
            binding.tvDataTimestamp.visibility = View.VISIBLE
        } else {
            binding.tvHeartRate.text = "-- ${getString(R.string.bpm)}"
            binding.tvBloodPressure.text = "--/--"
            binding.cardVitals.visibility = View.VISIBLE
            binding.tvDataTimestamp.visibility = View.GONE
        }
    }
    
    private fun loadStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.todayCount.collectLatest { count ->
                binding.tvTodayCount.text = count.toString()
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalCount.collectLatest { count ->
                binding.tvTotalCount.text = count.toString()
            }
        }
    }

    private fun showAddEntryDialog() {
        val dialog = AddEntryDialogFragment.newInstance()
        dialog.show(childFragmentManager, "AddEntryDialog")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

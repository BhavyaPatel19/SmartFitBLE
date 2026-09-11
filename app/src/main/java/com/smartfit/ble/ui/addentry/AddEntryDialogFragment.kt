package com.smartfit.ble.ui.addentry

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.smartfit.ble.R
import com.smartfit.ble.SmartFitApp
import com.smartfit.ble.databinding.DialogAddEntryBinding
import com.smartfit.ble.util.ToastUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class AddEntryDialogFragment : DialogFragment() {

    private var _binding: DialogAddEntryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEntryViewModel by viewModels {
        val app = requireActivity().application as SmartFitApp
        AddEntryViewModelFactory(app.appContainer.healthRepository)
    }

    private var selectedTimestamp = System.currentTimeMillis()
    private val calendar = Calendar.getInstance()

    private var hasPrefilledFields = false

    private val recordId: Long? by lazy {
        val value = arguments?.getLong(ARG_RECORD_ID, NO_RECORD_ID) ?: NO_RECORD_ID
        value.takeIf { it != NO_RECORD_ID }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_SmartFitBLE_FullScreenDialog)
        recordId?.let { viewModel.loadRecord(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupClickListeners()
        observeViewModel()
        updateDateTimeDisplay()
        updateDialogTitle()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener { dismiss() }
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener { saveEntry() }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSelectDateTime.setOnClickListener { showDateTimePicker() }
    }

    private fun observeViewModel() {

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.saveSuccess.collectLatest { success ->
                if (success) {
                    ToastUtil.showSuccess(
                        requireContext(),
                        getString(
                            if (recordId == null)
                                R.string.entry_saved
                            else
                                R.string.entry_updated
                        )
                    )
                    viewModel.resetSaveSuccess()
                    dismiss()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.editingRecord.collectLatest { record ->
                if (record != null && !hasPrefilledFields) {
                    hasPrefilledFields = true
                    binding.etHeartRate.setText(record.heartRate.toString())
                    binding.etSystolicBP.setText(record.systolicBP.toString())
                    binding.etDiastolicBP.setText(record.diastolicBP.toString())
                    selectedTimestamp = record.timestamp
                    updateDateTimeDisplay()
                    updateDialogTitle()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.showConfirmationDialog.collectLatest { shouldShow ->
                if (shouldShow) {
                    viewModel.onConfirmationDialogHandled()
                    val message = viewModel.warningMessage.value
                        ?: "These values are outside the normal range. Do you want to save anyway?"

                    AlertDialog.Builder(requireContext())
                        .setTitle("Warning")
                        .setMessage(message)
                        .setPositiveButton(getString(R.string.save)) { _, _ ->
                            val heartRate = binding.etHeartRate.text.toString().trim().toInt()
                            val systolic = binding.etSystolicBP.text.toString().trim().toInt()
                            val diastolic = binding.etDiastolicBP.text.toString().trim().toInt()

                            viewModel.submitRecord(
                                heartRate = heartRate,
                                systolicBP = systolic,
                                diastolicBP = diastolic,
                                timestamp = selectedTimestamp,
                                forceSave = true
                            )
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            }
        }
    }

    private fun saveEntry() {
        val hr = binding.etHeartRate.text?.toString()?.trim().orEmpty()
        val sys = binding.etSystolicBP.text?.toString()?.trim().orEmpty()
        val dia = binding.etDiastolicBP.text?.toString()?.trim().orEmpty()

        if (!validateInput(hr, sys, dia)) return

        if (selectedTimestamp > System.currentTimeMillis()) {
            ToastUtil.showError(requireContext(), "Cannot select a future date or time")
            return
        }

        viewModel.submitRecord(
            heartRate = hr.toInt(),
            systolicBP = sys.toInt(),
            diastolicBP = dia.toInt(),
            timestamp = selectedTimestamp
        )
    }

    private fun validateInput(hr: String, sys: String, dia: String): Boolean {
        var isValid = true

        if (hr.isBlank()) {
            binding.etHeartRate.error = getString(R.string.error_invalid_heart_rate)
            isValid = false
        } else {
            val value = hr.toIntOrNull()
            if (value == null || value < 40 || value > 200) {
                binding.etHeartRate.error = getString(R.string.error_invalid_heart_rate)
                isValid = false
            } else binding.etHeartRate.error = null
        }

        if (sys.isBlank()) {
            binding.etSystolicBP.error = getString(R.string.error_invalid_systolic)
            isValid = false
        } else {
            val value = sys.toIntOrNull()
            if (value == null || value < 70 || value > 250) {
                binding.etSystolicBP.error = getString(R.string.error_invalid_systolic)
                isValid = false
            } else binding.etSystolicBP.error = null
        }

        if (dia.isBlank()) {
            binding.etDiastolicBP.error = getString(R.string.error_invalid_diastolic)
            isValid = false
        } else {
            val value = dia.toIntOrNull()
            if (value == null || value < 40 || value > 150) {
                binding.etDiastolicBP.error = getString(R.string.error_invalid_diastolic)
                isValid = false
            } else binding.etDiastolicBP.error = null
        }

        if (isValid) {
            val s = sys.toInt()
            val d = dia.toInt()
            if (s <= d) {
                binding.etSystolicBP.error = getString(R.string.error_invalid_bp_range)
                isValid = false
            }
        }

        return isValid
    }

    private fun showDateTimePicker() {
        calendar.timeInMillis = selectedTimestamp
        val now = Calendar.getInstance()

        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->

                val chosenDate = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (chosenDate.timeInMillis > today.timeInMillis) {
                    ToastUtil.showError(requireContext(), "Future dates are not allowed")
                    return@DatePickerDialog
                }

                calendar.set(year, month, day)

                val timePicker = TimePickerDialog(
                    requireContext(),
                    { _, hour, minute ->

                        val chosenDateTime = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        if (chosenDateTime.timeInMillis > now.timeInMillis) {
                            ToastUtil.showError(requireContext(), "Future time is not allowed")
                            return@TimePickerDialog
                        }

                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        selectedTimestamp = calendar.timeInMillis
                        updateDateTimeDisplay()
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                )

                if (year == now.get(Calendar.YEAR) &&
                    month == now.get(Calendar.MONTH) &&
                    day == now.get(Calendar.DAY_OF_MONTH)
                ) {
                    timePicker.setOnShowListener {
                        val tp = timePicker.findViewById<android.widget.TimePicker>(
                            resources.getIdentifier("timePicker", "id", "android")
                        )

                        tp?.setOnTimeChangedListener { _, h, m ->
                            if (h > now.get(Calendar.HOUR_OF_DAY) ||
                                (h == now.get(Calendar.HOUR_OF_DAY) && m > now.get(Calendar.MINUTE))
                            ) {
                                tp.hour = now.get(Calendar.HOUR_OF_DAY)
                                tp.minute = now.get(Calendar.MINUTE)
                            }
                        }
                    }
                }

                timePicker.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePicker.datePicker.maxDate = now.timeInMillis
        datePicker.show()
    }


    private fun updateDateTimeDisplay() {
        val formatter = java.text.SimpleDateFormat(
            "MMM dd, yyyy HH:mm",
            java.util.Locale.getDefault()
        )
        binding.tvSelectedDateTime.text = formatter.format(selectedTimestamp)
    }

    private fun updateDialogTitle() {
        binding.topAppBar.title =
            getString(if (recordId == null) R.string.add_entry_title else R.string.edit_entry_title)

        binding.btnSave.text =
            getString(if (recordId == null) R.string.save else R.string.update_entry)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_RECORD_ID = "record_id"
        private const val NO_RECORD_ID = -1L

        fun newInstance(recordId: Long? = null): AddEntryDialogFragment {
            return AddEntryDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_RECORD_ID, recordId ?: NO_RECORD_ID)
                }
            }
        }
    }
}

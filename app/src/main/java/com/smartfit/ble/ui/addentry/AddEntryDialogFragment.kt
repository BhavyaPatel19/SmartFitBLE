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
                    binding.etPatientName.setText(record.patientName)
                    binding.etPatientAge.setText(record.patientAge?.toString() ?: "")
                    binding.etHeartRate.setText(record.heartRate.toString())
                    binding.etSpo2.setText(record.spo2?.toString() ?: "")
                    binding.etTemperature.setText(record.temperature?.let { "%.1f".format(it) } ?: "")
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
                            val patientName = binding.etPatientName.text.toString().trim()
                            val patientAge = binding.etPatientAge.text.toString().trim().toIntOrNull()
                            val heartRate = binding.etHeartRate.text.toString().trim().toInt()
                            val spo2 = binding.etSpo2.text.toString().trim().toInt()
                            val temperature = binding.etTemperature.text.toString().trim().toFloat()

                            viewModel.submitRecord(
                                patientName = patientName,
                                patientAge = patientAge,
                                heartRate = heartRate,
                                spo2 = spo2,
                                temperature = temperature,
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
        val patientName = binding.etPatientName.text?.toString()?.trim().orEmpty()
        val patientAgeStr = binding.etPatientAge.text?.toString()?.trim().orEmpty()
        val hr = binding.etHeartRate.text?.toString()?.trim().orEmpty()
        val spo2Str = binding.etSpo2.text?.toString()?.trim().orEmpty()
        val tempStr = binding.etTemperature.text?.toString()?.trim().orEmpty()

        if (!validateInput(patientName, patientAgeStr, hr, spo2Str, tempStr)) return

        if (selectedTimestamp > System.currentTimeMillis()) {
            ToastUtil.showError(requireContext(), "Cannot select a future date or time")
            return
        }

        val patientAge = patientAgeStr.toIntOrNull()
        viewModel.submitRecord(
            patientName = patientName,
            patientAge = patientAge,
            heartRate = hr.toInt(),
            spo2 = spo2Str.toInt(),
            temperature = tempStr.toFloat(),
            timestamp = selectedTimestamp
        )
    }

    private fun validateInput(
        patientName: String,
        patientAgeStr: String,
        hr: String,
        spo2Str: String,
        tempStr: String
    ): Boolean {
        var isValid = true

        if (patientName.isBlank()) {
            binding.etPatientName.error = getString(R.string.error_patient_name_required)
            isValid = false
        } else {
            binding.etPatientName.error = null
        }

        val age = patientAgeStr.toIntOrNull()
        if (patientAgeStr.isNotBlank() && (age == null || age !in 0..130)) {
            binding.etPatientAge.error = getString(R.string.error_patient_age_invalid)
            isValid = false
        } else {
            binding.etPatientAge.error = null
        }

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

        if (spo2Str.isBlank()) {
            binding.etSpo2.error = getString(R.string.error_invalid_spo2)
            isValid = false
        } else {
            val value = spo2Str.toIntOrNull()
            if (value == null || value < 70 || value > 100) {
                binding.etSpo2.error = getString(R.string.error_invalid_spo2)
                isValid = false
            } else binding.etSpo2.error = null
        }

        if (tempStr.isBlank()) {
            binding.etTemperature.error = getString(R.string.error_invalid_temperature)
            isValid = false
        } else {
            val value = tempStr.toFloatOrNull()
            if (value == null || value < 35f || value > 42f) {
                binding.etTemperature.error = getString(R.string.error_invalid_temperature)
                isValid = false
            } else binding.etTemperature.error = null
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

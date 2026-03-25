package com.smartfit.ble.ui.addentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartfit.ble.data.model.HealthRecord
import com.smartfit.ble.data.repository.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddEntryViewModel(private val repository: HealthRepository) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _editingRecord = MutableStateFlow<HealthRecord?>(null)
    val editingRecord: StateFlow<HealthRecord?> = _editingRecord.asStateFlow()

    private val _warningMessage = MutableStateFlow<String?>(null)
    val warningMessage: StateFlow<String?> = _warningMessage.asStateFlow()

    private val _showConfirmationDialog = MutableStateFlow(false)
    val showConfirmationDialog: StateFlow<Boolean> = _showConfirmationDialog.asStateFlow()

    private val NORMAL_HR_RANGE = 60..100
    private val NORMAL_SPO2_RANGE = 95..100
    private val NORMAL_TEMP_RANGE = 36.1f..37.2f

    fun loadRecord(recordId: Long) {
        viewModelScope.launch {
            _editingRecord.value = repository.getRecordById(recordId)
        }
    }

    fun submitRecord(
        patientName: String,
        patientAge: Int?,
        heartRate: Int,
        spo2: Int,
        temperature: Float,
        timestamp: Long = System.currentTimeMillis(),
        forceSave: Boolean = false
    ) {
        if (!forceSave && !checkForWarnings(heartRate, spo2, temperature)) {
            return
        }

        val normalizedPatientName = patientName.trim()
        val patientNameLower = normalizedPatientName.lowercase()

        viewModelScope.launch {
            _isSaving.value = true
            _saveSuccess.value = false
            try {
                val existing = _editingRecord.value
                if (existing == null) {
                    repository.insertRecord(
                        HealthRecord(
                            patientName = normalizedPatientName,
                            patientNameLower = patientNameLower,
                            patientAge = patientAge,
                            heartRate = heartRate,
                            spo2 = spo2,
                            temperature = temperature,
                            timestamp = timestamp
                        )
                    )
                } else {
                    repository.updateRecord(
                        existing.copy(
                            patientName = normalizedPatientName,
                            patientNameLower = patientNameLower,
                            patientAge = patientAge,
                            heartRate = heartRate,
                            spo2 = spo2,
                            temperature = temperature,
                            timestamp = timestamp
                        )
                    )
                }
                _saveSuccess.value = true
                _warningMessage.value = null
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun checkForWarnings(
        heartRate: Int,
        spo2: Int,
        temperature: Float
    ): Boolean {
        val warnings = mutableListOf<String>()

        if (heartRate !in NORMAL_HR_RANGE) {
            warnings += "Heart rate is outside the normal range (${NORMAL_HR_RANGE.first}-${NORMAL_HR_RANGE.last} bpm)."
        }
        if (spo2 !in NORMAL_SPO2_RANGE) {
            warnings += "SpO₂ is outside the normal range (${NORMAL_SPO2_RANGE.first}-${NORMAL_SPO2_RANGE.last}%)."
        }
        if (temperature !in NORMAL_TEMP_RANGE) {
            warnings += "Temperature is outside the normal range (${NORMAL_TEMP_RANGE.start}-${NORMAL_TEMP_RANGE.endInclusive}°C)."
        }

        return if (warnings.isNotEmpty()) {
            _warningMessage.value = warnings.joinToString("\n")
            _showConfirmationDialog.value = true
            false
        } else {
            _warningMessage.value = null
            _showConfirmationDialog.value = false
            true
        }
    }

    fun onConfirmationDialogHandled() {
        _showConfirmationDialog.value = false
    }

    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }
}

class AddEntryViewModelFactory(private val repository: HealthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddEntryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddEntryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

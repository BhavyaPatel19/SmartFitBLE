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

    // Normal ranges for "warning" (tighter than hard validation)
    private val NORMAL_HR_RANGE = 60..100
    private val NORMAL_SBP_RANGE = 90..120
    private val NORMAL_DBP_RANGE = 60..80

    fun loadRecord(recordId: Long) {
        viewModelScope.launch {
            _editingRecord.value = repository.getRecordById(recordId)
        }
    }


    fun submitRecord(
        heartRate: Int,
        systolicBP: Int,
        diastolicBP: Int,
        timestamp: Long = System.currentTimeMillis(),
        forceSave: Boolean = false
    ) {
        // If not forced, first check if values are abnormal
        if (!forceSave && !checkForWarnings(heartRate, systolicBP, diastolicBP)) {
            // Abnormal values detected; a confirmation dialog will be shown by the UI
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            _saveSuccess.value = false
            try {
                val existing = _editingRecord.value
                if (existing == null) {
                    repository.insertRecord(
                        HealthRecord(
                            heartRate = heartRate,
                            systolicBP = systolicBP,
                            diastolicBP = diastolicBP,
                            timestamp = timestamp
                        )
                    )
                } else {
                    repository.updateRecord(
                        existing.copy(
                            heartRate = heartRate,
                            systolicBP = systolicBP,
                            diastolicBP = diastolicBP,
                            timestamp = timestamp
                        )
                    )
                }
                _saveSuccess.value = true
                // Clear warning state after a successful save
                _warningMessage.value = null
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun checkForWarnings(
        heartRate: Int,
        systolicBP: Int,
        diastolicBP: Int
    ): Boolean {
        val warnings = mutableListOf<String>()

        if (heartRate !in NORMAL_HR_RANGE) {
            warnings += "Heart rate is outside the normal range (${NORMAL_HR_RANGE.first}-${NORMAL_HR_RANGE.last} bpm)."
        }
        if (systolicBP !in NORMAL_SBP_RANGE) {
            warnings += "Systolic BP is outside the normal range (${NORMAL_SBP_RANGE.first}-${NORMAL_SBP_RANGE.last} mmHg)."
        }
        if (diastolicBP !in NORMAL_DBP_RANGE) {
            warnings += "Diastolic BP is outside the normal range (${NORMAL_DBP_RANGE.first}-${NORMAL_DBP_RANGE.last} mmHg)."
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

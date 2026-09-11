package com.smartfit.ble.ui.graphs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartfit.ble.data.model.HealthRecord
import com.smartfit.ble.data.repository.HealthRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ChartPoint(
    val xIndex: Float,
    val y: Float,
    val timestamp: Long,
    val colorType: Int
)

class GraphsViewModel(private val repository: HealthRepository) : ViewModel() {

    private val recordsFlow = repository.getAllRecords()

    private val sortedRecordsFlow = recordsFlow
        .map { it.sortedBy { r -> r.timestamp } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val heartRatePoints: StateFlow<List<ChartPoint>> = sortedRecordsFlow
        .map { list ->
            list.mapIndexed { index, record ->


                val color = when {
                    record.heartRate < 60 || record.heartRate > 100 -> 2                     else -> 0
                }

                ChartPoint(
                    xIndex = index.toFloat(),
                    y = record.heartRate.toFloat(),
                    timestamp = record.timestamp,
                    colorType = color
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val systolicPoints: StateFlow<List<ChartPoint>> = sortedRecordsFlow
        .map { list ->
            list.mapIndexed { index, record ->

                val sbp = record.systolicBP
                val dbp = record.diastolicBP


                val color = when {
                    sbp >= 140 || dbp >= 90 -> 2
                    sbp >= 130 || dbp >= 80 -> 2
                    sbp in 120..129 && dbp < 80 -> 1
                    else -> 0
                }

                ChartPoint(
                    xIndex = index.toFloat(),
                    y = sbp.toFloat(),
                    timestamp = record.timestamp,
                    colorType = color
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val diastolicPoints: StateFlow<List<ChartPoint>> = sortedRecordsFlow
        .map { list ->
            list.mapIndexed { index, record ->

                val sbp = record.systolicBP
                val dbp = record.diastolicBP


                val color = when {
                    sbp >= 140 || dbp >= 90 -> 2
                    sbp >= 130 || dbp >= 80 -> 2
                    sbp in 120..129 && dbp < 80 -> 1
                    else -> 0
                }

                ChartPoint(
                    xIndex = index.toFloat(),
                    y = dbp.toFloat(),
                    timestamp = record.timestamp,
                    colorType = color
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
}

class GraphsViewModelFactory(private val repository: HealthRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GraphsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GraphsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}

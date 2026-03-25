package com.smartfit.ble.ui.history


import com.smartfit.ble.data.model.HealthRecord

sealed class HistoryItem {
    data class Header(val title: String) : HistoryItem()
    data class Record(val record: HealthRecord) : HistoryItem()
}

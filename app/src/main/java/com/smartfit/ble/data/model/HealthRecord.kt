package com.smartfit.ble.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "health_records")
data class HealthRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patientName: String = "",
    val patientNameLower: String = "",
    val patientAge: Int? = null,
    val heartRate: Int, // bpm
    val spo2: Int?, // SpO2 percentage (0-100), null if not measured
    val temperature: Float?, // body temperature °C, null if not measured
    val timestamp: Long = System.currentTimeMillis()
) {
    val spo2Display: String
        get() = spo2?.let { "$it%" } ?: "--"

    val temperatureDisplay: String
        get() = temperature?.let { "%.1f°C".format(it) } ?: "--"

    val date: Date
        get() = Date(timestamp)
}

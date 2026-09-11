    package com.smartfit.ble.data.model

    import androidx.room.Entity
    import androidx.room.PrimaryKey
    import java.util.Date

    @Entity(tableName = "health_records")
    data class HealthRecord(
        @PrimaryKey(autoGenerate = true)
        val id: Long = 0,
        val heartRate: Int, // bpm
        val systolicBP: Int, // mmHg
        val diastolicBP: Int, // mmHg
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val bloodPressure: String
            get() = "$systolicBP/$diastolicBP"

        val date: Date
            get() = Date(timestamp)
    }


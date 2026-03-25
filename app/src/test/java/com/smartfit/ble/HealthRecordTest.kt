package com.smartfit.ble


import com.smartfit.ble.data.model.HealthRecord
import org.junit.Test
import org.junit.Assert.assertEquals

class HealthRecordTest {

    @Test
    fun bloodPressure_isCorrect() {
        val record = HealthRecord(
            heartRate = 70,
            systolicBP = 120,
            diastolicBP = 80
        )

        assertEquals("120/80", record.bloodPressure)
    }
}

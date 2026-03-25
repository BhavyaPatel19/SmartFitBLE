package com.smartfit.ble.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.smartfit.ble.data.model.HealthRecord
import kotlinx.coroutines.tasks.await

class FirebaseHealthDataSource {

    private val firestore = FirebaseFirestore.getInstance()

    suspend fun uploadHealthRecord(uid: String, record: HealthRecord) {
        val data = mutableMapOf<String, Any>(
            "patientName" to record.patientName,
            "patientNameLower" to record.patientNameLower,
            "heartRate" to record.heartRate,
            "timestamp" to record.timestamp
        )
        record.patientAge?.let { data["patientAge"] = it }
        record.spo2?.let { data["spo2"] = it }
        record.temperature?.let { data["temperature"] = it }
        firestore
            .collection("users")
            .document(uid)
            .collection("health_records")
            .add(data)
            .await()
    }

    /** 🔄 BACKUP RESTORE */
    suspend fun fetchAllHealthRecords(uid: String): List<HealthRecord> {
        val snapshot = firestore
            .collection("users")
            .document(uid)
            .collection("health_records")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val heartRate = doc.getLong("heartRate")?.toInt() ?: return@mapNotNull null
            val timestamp = doc.getLong("timestamp") ?: return@mapNotNull null
            val spo2 = doc.getLong("spo2")?.toInt()
            val temperature = (doc.get("temperature") as? Number)?.toFloat()
            val patientName = doc.getString("patientName").orEmpty()
            val patientNameLower = doc.getString("patientNameLower")
                ?: patientName.lowercase()
            val patientAge = doc.getLong("patientAge")?.toInt()

            HealthRecord(
                patientName = patientName,
                patientNameLower = patientNameLower,
                patientAge = patientAge,
                heartRate = heartRate,
                spo2 = spo2,
                temperature = temperature,
                timestamp = timestamp
            )
        }
    }
}

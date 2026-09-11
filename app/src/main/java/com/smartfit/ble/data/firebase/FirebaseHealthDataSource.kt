package com.smartfit.ble.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.smartfit.ble.data.model.HealthRecord
import kotlinx.coroutines.tasks.await

class FirebaseHealthDataSource {

    private val firestore = FirebaseFirestore.getInstance()

    suspend fun uploadHealthRecord(uid: String, record: HealthRecord) {
        firestore
            .collection("users")
            .document(uid)
            .collection("health_records")
            .add(
                mapOf(
                    "heartRate" to record.heartRate,
                    "systolicBP" to record.systolicBP,
                    "diastolicBP" to record.diastolicBP,
                    "timestamp" to record.timestamp
                )
            )
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
            val systolic = doc.getLong("systolicBP")?.toInt() ?: return@mapNotNull null
            val diastolic = doc.getLong("diastolicBP")?.toInt() ?: return@mapNotNull null
            val timestamp = doc.getLong("timestamp") ?: return@mapNotNull null

            HealthRecord(
                heartRate = heartRate,
                systolicBP = systolic,
                diastolicBP = diastolic,
                timestamp = timestamp
            )
        }
    }
}

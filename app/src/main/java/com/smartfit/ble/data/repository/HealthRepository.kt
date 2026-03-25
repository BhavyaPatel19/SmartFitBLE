package com.smartfit.ble.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.smartfit.ble.data.database.HealthRecordDao
import com.smartfit.ble.data.firebase.FirebaseHealthDataSource
import com.smartfit.ble.data.model.HealthRecord
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class HealthRepository(
    private val dao: HealthRecordDao,
    private val firebase: FirebaseHealthDataSource
) {

    /* -------------------- LOCAL (ROOM) -------------------- */

    fun getAllRecords(): Flow<List<HealthRecord>> =
        dao.getAllRecords()

    fun observeLatestRecord(): Flow<HealthRecord?> =
        dao.observeLatestRecord()

    fun getRecentRecords(limit: Int): Flow<List<HealthRecord>> =
        dao.getRecentRecords(limit)

    fun getRecordsByDateRange(start: Long, end: Long): Flow<List<HealthRecord>> =
        dao.getRecordsByDateRange(start, end)

    fun getTodayRecordsCount(): Flow<Int> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        return dao.getTodayRecordsCount(start, end)
    }

    suspend fun getLatestRecord(): HealthRecord? =
        dao.getLatestRecord()

    suspend fun getRecordById(id: Long): HealthRecord? =
        dao.getRecordById(id)

    /* -------------------- INSERT (ROOM + FIREBASE) -------------------- */

    suspend fun insertRecord(record: HealthRecord) {

        // 1️⃣ Save locally first
        dao.insertRecord(record)

        // 2️⃣ Always resolve real Firebase UID
        val firebaseUid = FirebaseAuth.getInstance()
            .currentUser
            ?.uid ?: return

        // 3️⃣ Upload to Firestore
        firebase.uploadHealthRecord(firebaseUid, record)
    }

    /* -------------------- BACKUP RESTORE -------------------- */

    suspend fun restoreFromFirebaseIfNeeded() {

        val firebaseUid = FirebaseAuth.getInstance()
            .currentUser
            ?.uid ?: return

        try {
            val remoteRecords = firebase.fetchAllHealthRecords(firebaseUid)

            for (record in remoteRecords) {
                val exists = dao.existsByTimestamp(record.timestamp) > 0
                if (!exists) {
                    dao.insertRecord(record)
                }
            }

        } catch (e: Exception) {
            // 🔥 Never crash app due to cloud failure
            e.printStackTrace()
        }
    }

    /* -------------------- UPDATE / DELETE -------------------- */

    suspend fun updateRecord(record: HealthRecord) =
        dao.updateRecord(record)

    suspend fun deleteRecord(record: HealthRecord) =
        dao.deleteRecord(record)

    suspend fun deleteRecordById(id: Long) =
        dao.deleteRecordById(id)

    suspend fun clearLocalData() =
        dao.deleteAll()
}
package com.smartfit.ble.data.database

import androidx.room.*
import com.smartfit.ble.data.model.HealthRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthRecordDao {
    
    @Query("SELECT * FROM health_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<HealthRecord>>
    
    @Query("SELECT * FROM health_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentRecords(limit: Int): Flow<List<HealthRecord>>
    
    @Query("SELECT * FROM health_records WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getRecordsByDateRange(startTime: Long, endTime: Long): Flow<List<HealthRecord>>
    
    @Query("SELECT * FROM health_records WHERE id = :id")
    suspend fun getRecordById(id: Long): HealthRecord?
    
    @Query("SELECT * FROM health_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecord(): HealthRecord?
    
    @Query("SELECT * FROM health_records ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestRecord(): Flow<HealthRecord?>
    
    @Query("SELECT COUNT(*) FROM health_records WHERE timestamp >= :startOfDay AND timestamp < :endOfDay")
    fun getTodayRecordsCount(startOfDay: Long, endOfDay: Long): Flow<Int>

    @Insert
    suspend fun insertRecord(record: HealthRecord): Long
    
    @Update
    suspend fun updateRecord(record: HealthRecord)
    
    @Delete
    suspend fun deleteRecord(record: HealthRecord)
    
    @Query("DELETE FROM health_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)
    
    @Query("DELETE FROM health_records")
    suspend fun deleteAllRecords()

    @Query("SELECT COUNT(*) FROM health_records WHERE timestamp = :timestamp")
    suspend fun existsByTimestamp(timestamp: Long): Int

    @Query("DELETE FROM health_records")
    suspend fun deleteAll()

}


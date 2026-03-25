package com.smartfit.ble.di

import android.content.Context
import com.smartfit.ble.data.SessionManager
import com.smartfit.ble.data.ble.BleRepository
import com.smartfit.ble.data.database.HealthDatabase
import com.smartfit.ble.data.firebase.FirebaseHealthDataSource
import com.smartfit.ble.data.repository.HealthRepository

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: HealthDatabase by lazy {
        HealthDatabase.getDatabase(appContext)
    }

    private val sessionManager: SessionManager by lazy {
        SessionManager(appContext)
    }

    private val firebaseHealthDataSource: FirebaseHealthDataSource by lazy {
        FirebaseHealthDataSource()
    }

    val healthRepository: HealthRepository by lazy {
        HealthRepository(
            dao = database.healthRecordDao(),
            firebase = firebaseHealthDataSource,

        )
    }

    // BleRepository is now created by a factory to ensure it's only created when needed
    fun createBleRepository(): BleRepository {
        return BleRepository(appContext, healthRepository)
    }
}

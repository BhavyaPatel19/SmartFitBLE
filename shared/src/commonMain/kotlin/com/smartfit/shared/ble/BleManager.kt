package com.smartfit.shared.ble

import kotlinx.coroutines.flow.Flow

expect class BleManager() {
    val heartRate: Flow<Int>
    suspend fun scanDevices(): List<BleDevice>
    suspend fun connectTo(address: String): Boolean
    suspend fun startMeasurement()
    suspend fun stopMeasurement()
}

package com.smartfit.shared.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

actual class BleManager actual constructor() {
    private val _heartRate = MutableSharedFlow<Int>(replay = 1)
    actual val heartRate: Flow<Int> get() = _heartRate

    actual suspend fun scanDevices(): List<BleDevice> = emptyList()
    actual suspend fun connectTo(address: String): Boolean = false
    actual suspend fun startMeasurement() {}
    actual suspend fun stopMeasurement() {}
}

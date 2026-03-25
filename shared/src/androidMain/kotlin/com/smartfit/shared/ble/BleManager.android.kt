package com.smartfit.shared.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

actual class BleManager actual constructor() {
    private val _heartRate = MutableSharedFlow<Int>(replay = 1)
    actual val heartRate: Flow<Int> get() = _heartRate

    actual suspend fun scanDevices(): List<BleDevice> {
        // TODO: Implement Android BLE scanning in app layer or here
        return emptyList()
    }

    actual suspend fun connectTo(address: String): Boolean {
        // TODO: Implement real connection
        return false
    }

    actual suspend fun startMeasurement() {
        // TODO: Emit dummy values for now
        _heartRate.emit(72)
    }

    actual suspend fun stopMeasurement() {
        // no-op
    }
}

package com.smartfit.ble.data.ble.model

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val state: DeviceConnectionState = DeviceConnectionState.IDLE
) {
    val displayName: String
        get() = name.ifBlank { "Device ${address.takeLast(4)}" }
}

enum class DeviceConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED
}

data class ConnectedDeviceInfo(
    val name: String,
    val address: String,
    val serialNumber: String? = null,
    val firmwareVersion: String? = null,
    val batteryPercent: Int? = null
) {
    val displayName: String
        get() = if (name.isNotBlank()) name else "Device ${address.takeLast(4)}"
}


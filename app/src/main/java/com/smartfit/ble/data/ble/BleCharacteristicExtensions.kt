package com.smartfit.ble.data.ble

import android.bluetooth.BluetoothGattCharacteristic

internal fun BluetoothGattCharacteristic.readUtf8(): String? {
    val data = value ?: return null
    if (data.isEmpty()) return null
    return String(data, Charsets.UTF_8).trim().ifBlank { null }
}

internal fun BluetoothGattCharacteristic.readBatteryPercentage(): Int? {
    val data = value ?: return null
    if (data.isEmpty()) return null
    return data[0].toInt().coerceIn(0, 100)
}


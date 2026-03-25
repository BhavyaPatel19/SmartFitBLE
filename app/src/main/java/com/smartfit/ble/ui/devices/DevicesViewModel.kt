package com.smartfit.ble.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.smartfit.ble.data.ble.BleRepository
import com.smartfit.ble.data.ble.model.BleDevice

class DevicesViewModel(
    private val bleRepository: BleRepository
) : ViewModel() {

    val devices = bleRepository.devices
    val isScanning = bleRepository.isScanning
    val connectionState = bleRepository.connectionState
    val connectedInfo = bleRepository.connectedInfo
    val events = bleRepository.events

    // BP + pulse from BLE
    val bpMeasurements = bleRepository.bpMeasurements

    fun startScan() = bleRepository.startScan()

    fun stopScan() = bleRepository.stopScan()

    fun connectToDevice(device: BleDevice) = bleRepository.connectToDevice(device)

    fun disconnect() = bleRepository.disconnect()

    override fun onCleared() {
        super.onCleared()
        bleRepository.stopScan()
    }
}

class DevicesViewModelFactory(
    private val bleRepository: BleRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DevicesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DevicesViewModel(bleRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

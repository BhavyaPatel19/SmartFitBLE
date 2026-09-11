package com.smartfit.ble.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.smartfit.ble.data.ble.model.BleDevice
import com.smartfit.ble.data.ble.model.ConnectedDeviceInfo
import com.smartfit.ble.data.ble.model.DeviceConnectionState
import com.smartfit.ble.data.firebase.FirebaseHealthDataSource
import com.smartfit.ble.data.model.HealthRecord
import com.smartfit.ble.data.repository.HealthRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class BleRepository(
    private val context: Context,
    private val healthRepository: HealthRepository
) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firebaseHealthDataSource = FirebaseHealthDataSource()

    private val devicesMap = LinkedHashMap<String, BleDevice>()

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectedInfo = MutableStateFlow<ConnectedDeviceInfo?>(null)
    val connectedInfo: StateFlow<ConnectedDeviceInfo?> = _connectedInfo.asStateFlow()

    private val _connectionState = MutableStateFlow(DeviceConnectionState.IDLE)
    val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<BleEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BleEvent> = _events.asSharedFlow()


    private val _bpMeasurements = MutableSharedFlow<BloodPressureMeasurement>(
        extraBufferCapacity = 8
    )
    val bpMeasurements: SharedFlow<BloodPressureMeasurement> = _bpMeasurements.asSharedFlow()

    private var scanCallback: ScanCallback? = null
    private var scanTimeoutJob: Job? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedAddress: String? = null

    private val pendingReads = ArrayDeque<BluetoothGattCharacteristic>()


    private val TARGET_SERVICE_UUID =
        ParcelUuid.fromString(UUID_BLOOD_PRESSURE_SERVICE)

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter ?: run {
            emitError(BleError.NOT_SUPPORTED)
            return
        }

        val scanner = adapter.bluetoothLeScanner ?: run {
            emitError(BleError.NOT_SUPPORTED)
            return
        }

        stopScan()
        devicesMap.clear()
        publishDevices()

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(TARGET_SERVICE_UUID)
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                emitError(BleError.SCAN_FAILED)
                stopScan()
            }
        }

        scanner.startScan(listOf(scanFilter), scanSettings, callback)

        scanCallback = callback
        _isScanning.value = true

        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(SCAN_WINDOW_MS)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val adapter = bluetoothAdapter ?: return
        val scanner = adapter.bluetoothLeScanner ?: return

        scanCallback?.let { scanner.stopScan(it) }

        scanCallback = null
        _isScanning.value = false
        scanTimeoutJob?.cancel()
    }


    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BleDevice) {
        val adapter = bluetoothAdapter ?: return

        stopScan()

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()

        _connectionState.value = DeviceConnectionState.CONNECTING
        updateDeviceState(device.address, DeviceConnectionState.CONNECTING)

        bluetoothGatt = adapter.getRemoteDevice(device.address)
            .connectGatt(context, false, gattCallback)

        connectedAddress = device.address

        _connectedInfo.value = ConnectedDeviceInfo(
            name = device.displayName,
            address = device.address
        )
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()

        bluetoothGatt = null
        connectedAddress = null
        pendingReads.clear()

        _connectedInfo.value = null
        _connectionState.value = DeviceConnectionState.IDLE
        updateDeviceState(null, DeviceConnectionState.IDLE)

        context.stopService(Intent(context, HealthService::class.java))
    }


    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val name = (device.name ?: result.scanRecord?.deviceName ?: "").trim()

        if (name.isBlank() || name.equals("Unknown Device", ignoreCase = true)) return

        val state =
            if (address == connectedAddress) DeviceConnectionState.CONNECTED
            else DeviceConnectionState.IDLE

        devicesMap[address] = BleDevice(
            name = name,
            address = address,
            rssi = result.rssi,
            state = state
        )

        publishDevices()
    }

    private fun publishDevices() {
        _devices.value = devicesMap.values.sortedBy { it.displayName }
    }

    private fun updateDeviceState(address: String?, newState: DeviceConnectionState) {
        if (address == null) {
            val updated = devicesMap.mapValues { (_, dev) -> dev.copy(state = newState) }
            devicesMap.clear()
            devicesMap.putAll(updated)
        } else {
            devicesMap[address]?.let { dev ->
                devicesMap[address] = dev.copy(state = newState)
            }
        }
        publishDevices()
    }

    private fun emitError(error: BleError) {
        _events.tryEmit(BleEvent.Error(error))
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedAddress = gatt.device.address
                _connectionState.value = DeviceConnectionState.CONNECTED
                updateDeviceState(gatt.device.address, DeviceConnectionState.CONNECTED)

                _connectedInfo.value = _connectedInfo.value?.copy(
                    name = gatt.device.name ?: "",
                    address = gatt.device.address
                ) ?: ConnectedDeviceInfo(
                    name = gatt.device.name ?: "",
                    address = gatt.device.address
                )
                context.startService(Intent(context, HealthService::class.java))
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = DeviceConnectionState.IDLE
                connectedAddress = null
                updateDeviceState(gatt.device.address, DeviceConnectionState.IDLE)
                _connectedInfo.value = null
                pendingReads.clear()
                context.stopService(Intent(context, HealthService::class.java))
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            pendingReads.clear()


            gatt.getService(DEVICE_INFO_SERVICE_UUID)?.let { svc ->
                svc.getCharacteristic(SERIAL_NUMBER_UUID)?.let { pendingReads.add(it) }
                svc.getCharacteristic(FIRMWARE_UUID)?.let { pendingReads.add(it) }
            }


            gatt.getService(BATTERY_SERVICE_UUID)
                ?.getCharacteristic(BATTERY_LEVEL_UUID)
                ?.let { pendingReads.add(it) }


            var cccdWritten = false

            val bpService = gatt.getService(BLOOD_PRESSURE_SERVICE_UUID)
            if (bpService != null) {
                val bpChar = bpService.getCharacteristic(PRESSURE_MEASUREMENT_CHAR_UUID)
                if (bpChar != null) {
                    gatt.setCharacteristicNotification(bpChar, true)

                    val descriptor = bpChar.getDescriptor(CLIENT_CHAR_CONFIG_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                        cccdWritten = true
                    }
                }
            }


            if (!cccdWritten) {
                readNextCharacteristic(gatt)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {

            readNextCharacteristic(gatt)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {
                    SERIAL_NUMBER_UUID ->
                        _connectedInfo.value =
                            _connectedInfo.value?.copy(
                                serialNumber = characteristic.readUtf8()
                            )

                    FIRMWARE_UUID ->
                        _connectedInfo.value =
                            _connectedInfo.value?.copy(
                                firmwareVersion = characteristic.readUtf8()
                            )

                    BATTERY_LEVEL_UUID ->
                        _connectedInfo.value =
                            _connectedInfo.value?.copy(
                                batteryPercent = characteristic.readBatteryPercentage()
                            )
                }
            }
            readNextCharacteristic(gatt)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == PRESSURE_MEASUREMENT_CHAR_UUID) {
                val data = characteristic.value ?: return
                val bp = parsePressureMeasurement(data) ?: return

                scope.launch {
                    // 1) emit to UI
                    _bpMeasurements.emit(bp)

                    // 2) build record once
                    val hr = bp.pulse ?: 0   // use pulse as heartRate
                    val record = HealthRecord(
                        heartRate = hr,
                        systolicBP = bp.systolic,
                        diastolicBP = bp.diastolic,
                        timestamp = bp.timestamp
                    )

                    // 3) save locally → dashboard, history, graphs all update
                    healthRepository.insertRecord(record)

                    // 4) upload to Firebase when a user is logged in

                }
            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun readNextCharacteristic(gatt: BluetoothGatt) {
        if (pendingReads.isEmpty()) return
        val next = pendingReads.removeFirst()
        gatt.readCharacteristic(next)
    }


    private fun parsePressureMeasurement(data: ByteArray): BloodPressureMeasurement? {
        if (data.size < 5) return null

        val systolic = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        val diastolic = (data[3].toInt() and 0xFF) or ((data[4].toInt() and 0xFF) shl 8)

        val pulse =
            if (data.size >= 16)
                (data[14].toInt() and 0xFF) or ((data[15].toInt() and 0xFF) shl 8)
            else null

        return BloodPressureMeasurement(
            systolic = systolic,
            diastolic = diastolic,
            pulse = pulse,
            timestamp = System.currentTimeMillis()
        )
    }


    sealed interface BleEvent {
        data class Error(val reason: BleError) : BleEvent
    }

    enum class BleError {
        NOT_SUPPORTED,
        SCAN_FAILED
    }

    data class BloodPressureMeasurement(
        val systolic: Int,
        val diastolic: Int,
        val pulse: Int?,
        val timestamp: Long
    )

    companion object {
        private const val TAG = "BleRepository"
        private const val SCAN_WINDOW_MS = 30_000L

        private val DEVICE_INFO_SERVICE_UUID =
            UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        private val SERIAL_NUMBER_UUID =
            UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
        private val FIRMWARE_UUID =
            UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

        private val BATTERY_SERVICE_UUID =
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        private const val UUID_BLOOD_PRESSURE_SERVICE =
            "56484AAE-A8EB-4A97-AC19-A8EA6373E05A"
        private const val PRESSURE_MEASUREMENT_CHAR =
            "2DB34480-BCE5-4BB7-9F56-55BD202317C5"

        val BLOOD_PRESSURE_SERVICE_UUID: UUID =
            UUID.fromString(UUID_BLOOD_PRESSURE_SERVICE)
        val PRESSURE_MEASUREMENT_CHAR_UUID: UUID =
            UUID.fromString(PRESSURE_MEASUREMENT_CHAR)

        private val CLIENT_CHAR_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

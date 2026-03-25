package com.smartfit.ble.ui.devices

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smartfit.ble.R
import com.smartfit.ble.SmartFitApp
import com.smartfit.ble.data.ble.BleRepository
import com.smartfit.ble.data.ble.BleRepository.BleError
import com.smartfit.ble.data.ble.model.BleDevice
import com.smartfit.ble.data.ble.model.ConnectedDeviceInfo
import com.smartfit.ble.data.ble.model.DeviceConnectionState
import com.smartfit.ble.databinding.FragmentDevicesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DevicesFragment : Fragment() {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    private val bluetoothManager by lazy {
        requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    }
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val deviceAdapter = BleDeviceAdapter(::onDeviceSelected)

    private val appContainer by lazy { (requireActivity().application as SmartFitApp).appContainer }
    private val viewModel: DevicesViewModel by viewModels {
        DevicesViewModelFactory(appContainer.createBleRepository())
    }

    private var pendingPermissionAction: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        if (granted) {
            pendingPermissionAction?.invoke()
        } else {
            showPermissionDialog()
        }
        pendingPermissionAction = null
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bluetoothAdapter?.isEnabled == true) {
            viewModel.startScan()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = deviceAdapter

        binding.btnScan.setOnClickListener { toggleScan() }
        binding.btnDisconnect.setOnClickListener { viewModel.disconnect() }

        observeViewModel()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopScan()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.devices.collectLatest { devices ->
                        deviceAdapter.submitList(devices)
                        binding.rvDevices.isVisible = devices.isNotEmpty()
                        binding.emptyStateGroup.isVisible = devices.isEmpty()
                    }
                }
                launch {
                    viewModel.isScanning.collectLatest { isScanning ->
                        updateScanUi(isScanning, viewModel.connectionState.value)
                    }
                }
                launch {
                    viewModel.connectionState.collectLatest { state ->
                        updateScanUi(viewModel.isScanning.value, state)
                    }
                }
                launch {
                    viewModel.connectedInfo.collectLatest { info ->
                        binding.cardConnected.isVisible = info != null
                        binding.btnDisconnect.isVisible = info != null
                        if (info != null) {
                            binding.tvConnectedDevice.text = info.displayName
                            binding.tvSerial.text =
                                getString(R.string.serial_number_value, info.serialNumber ?: "--")
                            binding.tvFirmware.text =
                                getString(R.string.firmware_version_value, info.firmwareVersion ?: "--")
                            val batteryText = info.batteryPercent?.let { "$it%" } ?: "--"
                            binding.tvBattery.text =
                                getString(R.string.battery_status_value, batteryText)
                        } else {
                            binding.tvSerial.text = getString(R.string.serial_number_value, "--")
                            binding.tvFirmware.text = getString(R.string.firmware_version_value, "--")
                            binding.tvBattery.text = getString(R.string.battery_status_value, "--")
                        }
                        updateStatusText(info)
                    }
                }


                launch {
                    viewModel.bpMeasurements.collectLatest {
                        showSnackbar("Reading collected successfully")
                    }
                }

                launch {
                    viewModel.events.collectLatest { event ->
                        when (event) {
                            is BleRepository.BleEvent.Error -> handleBleError(event.reason)
                        }
                    }
                }
            }
        }
    }

    private fun toggleScan() {
        if (viewModel.isScanning.value) {
            viewModel.stopScan()
        } else {
            startScanFlow()
        }
    }

    private fun startScanFlow() {
        if (hasAllPermissions()) {
            beginScan()
        } else {
            requestPermissions { beginScan() }
        }
    }

    private fun beginScan() {
        val adapter = bluetoothAdapter ?: run {
            showSnackbar(getString(R.string.ble_not_supported))
            return
        }
        if (!adapter.isEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        viewModel.startScan()
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        return permissions.toTypedArray()
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(requireContext(), perm) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun onDeviceSelected(device: BleDevice) {
        if (hasAllPermissions()) {
            viewModel.connectToDevice(device)
        } else {
            requestPermissions { viewModel.connectToDevice(device) }
        }
    }

    private fun requestPermissions(onGranted: () -> Unit) {
        pendingPermissionAction = onGranted
        permissionLauncher.launch(requiredPermissions())
    }

    private fun updateScanUi(isScanning: Boolean, connectionState: DeviceConnectionState) {
        binding.progressScan.isVisible = isScanning
        binding.btnScan.text = getString(
            if (isScanning) R.string.stop_scan else R.string.scan_devices
        )
        binding.btnScan.isEnabled = connectionState != DeviceConnectionState.CONNECTING
        updateStatusText(viewModel.connectedInfo.value)
    }

    private fun updateStatusText(connectedInfo: ConnectedDeviceInfo?) {
        binding.tvStatus.text = when {
            viewModel.isScanning.value -> getString(R.string.device_status_scanning)
            viewModel.connectionState.value == DeviceConnectionState.CONNECTING && connectedInfo != null ->
                getString(R.string.device_status_connecting, connectedInfo.displayName)
            viewModel.connectionState.value == DeviceConnectionState.CONNECTED && connectedInfo != null ->
                getString(R.string.device_status_connected, connectedInfo.displayName)
            else -> getString(R.string.device_status_idle)
        }
    }

    private fun handleBleError(error: BleError) {
        val message = when (error) {
            BleError.NOT_SUPPORTED -> getString(R.string.ble_not_supported)
            BleError.SCAN_FAILED -> getString(R.string.scan_failed)
        }
        showSnackbar(message)
    }

    private fun showSnackbar(message: String) {
        _binding?.let { binding ->
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showPermissionDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.permissions_required)
            .setMessage(R.string.permissions_rationale)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

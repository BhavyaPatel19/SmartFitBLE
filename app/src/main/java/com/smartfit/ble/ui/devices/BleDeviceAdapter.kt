package com.smartfit.ble.ui.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartfit.ble.R
import com.smartfit.ble.data.ble.model.BleDevice
import com.smartfit.ble.data.ble.model.DeviceConnectionState
import com.smartfit.ble.databinding.ItemBleDeviceBinding

class BleDeviceAdapter(
    private val onActionClicked: (BleDevice) -> Unit
) : ListAdapter<BleDevice, BleDeviceAdapter.DeviceViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemBleDeviceBinding.inflate(inflater, parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemBleDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: BleDevice) = with(binding) {
            tvName.text = device.displayName
            tvAddress.text = device.address
            tvRssi.text = root.context.getString(R.string.device_rssi_value, device.rssi)

            when (device.state) {
                DeviceConnectionState.IDLE -> {
                    btnAction.text = root.context.getString(R.string.connect)
                    btnAction.isEnabled = true
                    tvState.isVisible = false
                }

                DeviceConnectionState.CONNECTING -> {
                    btnAction.text = root.context.getString(R.string.connecting)
                    btnAction.isEnabled = false
                    tvState.isVisible = true
                    tvState.text = root.context.getString(R.string.connecting)
                }

                DeviceConnectionState.CONNECTED -> {
                    btnAction.text = root.context.getString(R.string.connected)
                    btnAction.isEnabled = false
                    tvState.isVisible = true
                    tvState.text = root.context.getString(R.string.connected)
                }
            }

            btnAction.setOnClickListener {
                if (device.state == DeviceConnectionState.IDLE) {
                    onActionClicked(device)
                }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<BleDevice>() {
        override fun areItemsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean =
            oldItem.address == newItem.address

        override fun areContentsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean =
            oldItem == newItem
    }
}


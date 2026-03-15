package com.plantsensor.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.plantsensor.app.data.BleDevice
import com.plantsensor.app.databinding.ItemDeviceBinding

class DeviceListAdapter(
    private val onDeviceClick: (BleDevice) -> Unit
) : ListAdapter<BleDevice, DeviceListAdapter.DeviceViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BleDevice>() {
            override fun areItemsTheSame(a: BleDevice, b: BleDevice) = a.address == b.address
            override fun areContentsTheSame(a: BleDevice, b: BleDevice) = a == b
        }
    }

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: BleDevice) {
            binding.tvDeviceName.text = device.name
            binding.tvDeviceAddress.text = device.address
            binding.tvRssi.text = "${device.rssi} dBm"
            binding.root.setOnClickListener { onDeviceClick(device) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

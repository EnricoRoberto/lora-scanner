package com.lorascanner.app.ui

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lorascanner.app.R
import com.lorascanner.app.databinding.DialogDevicePickerBinding
import com.lorascanner.app.databinding.ItemDeviceBinding
import kotlinx.coroutines.launch

class DevicePickerDialog(
    private val onDeviceSelected: (BluetoothDevice) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogDevicePickerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScannerViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = DialogDevicePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = DeviceAdapter { device ->
            onDeviceSelected(device)
            dismiss()
        }
        binding.recyclerDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDevices.adapter = adapter

        binding.btnStartScan.setOnClickListener {
            viewModel.startScan()
        }

        binding.btnSimMode.setOnClickListener {
            viewModel.startSimulation()
            dismiss()
        }

        lifecycleScope.launch {
            viewModel.discoveredDevices.collect { devices ->
                adapter.submitList(devices)
                binding.tvNoDevices.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewModel.startScan()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class DeviceAdapter(
    private val onClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    private val items = mutableListOf<BluetoothDevice>()

    @Suppress("MissingPermission")
    fun submitList(list: List<BluetoothDevice>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    @Suppress("MissingPermission")
    override fun onBindViewHolder(holder: VH, position: Int) {
        val device = items[position]
        holder.binding.tvDeviceName.text = device.name ?: "Unknown Device"
        holder.binding.tvDeviceAddress.text = device.address
        holder.itemView.setOnClickListener { onClick(device) }
    }

    override fun getItemCount() = items.size

    class VH(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)
}

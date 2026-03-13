package com.lorascanner.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.lorascanner.app.R
import com.lorascanner.app.ble.ConnectionState
import com.lorascanner.app.databinding.FragmentScanBinding
import com.lorascanner.app.ui.adapter.PacketAdapter
import com.lorascanner.app.utils.ExportUtils
import kotlinx.coroutines.launch

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScannerViewModel by activityViewModels()
    private lateinit var adapter: PacketAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PacketAdapter()
        binding.recyclerPackets.layoutManager = LinearLayoutManager(requireContext()).apply {
            reverseLayout = true
            stackFromEnd = true
        }
        binding.recyclerPackets.adapter = adapter

        // Scan / Stop button
       binding.btnScan.setOnClickListener {
        // La connessione si gestisce dalla tab Connetti
            findNavController().navigate(R.id.connectFragment)
        }    

        binding.btnSimulate.setOnClickListener {
            if (viewModel.connectionState.value == ConnectionState.RECEIVING) {
                viewModel.stopSimulation()
            } else {
                viewModel.startSimulation()
            }
        }

        binding.btnExport.setOnClickListener {
            ExportUtils.exportCsv(requireContext(), viewModel.packets.value)
        }

        // Observe state
        lifecycleScope.launch {
            viewModel.connectionState.collect { state ->
                updateConnectionUI(state)
            }
        }
        lifecycleScope.launch {
            viewModel.packets.collect { packets ->
                adapter.submitList(packets.reversed().take(200))
                binding.tvPacketCount.text = "${packets.size} packets"
            }
        }
        lifecycleScope.launch {
            viewModel.stats.collect { stats ->
                binding.tvAvgRssi.text = "Avg RSSI: ${stats.avgRssi.toInt()} dBm"
                binding.tvAvgSnr.text  = "Avg SNR: ${"%.1f".format(stats.avgSnr)} dB"
                binding.tvNodes.text   = "Nodes: ${stats.uniqueNodes}"
                binding.tvPpm.text     = "${"%.0f".format(stats.packetsPerMinute)} pkt/min"
            }
        }
    }

    private fun updateConnectionUI(state: ConnectionState) {
        val isReceiving = state == ConnectionState.RECEIVING
        binding.statusIndicator.isActivated = isReceiving
        binding.tvStatus.text = state.label
        binding.btnScan.text = when (state) {
            ConnectionState.RECEIVING -> "Disconnect"
            ConnectionState.SCANNING  -> "Stop Scan"
            else -> "Scan Devices"
        }
        binding.btnSimulate.text = if (isReceiving && viewModel.isScanning.value.not())
            "Stop Sim" else "▶ Demo Mode"
    }

    private fun showDevicePickerOrSim() {
        viewModel.startScan()
        // After a delay, if nothing found offer simulation
        lifecycleScope.launch {
            kotlinx.coroutines.delay(3000)
            if (viewModel.discoveredDevices.value.isEmpty()) {
                // Show a dialog or auto-start simulation
            }
        }
    }

    private fun showDevicePicker() {
        val dialog = DevicePickerDialog { device ->
            viewModel.connect(device)
        }
        dialog.show(childFragmentManager, "device_picker")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

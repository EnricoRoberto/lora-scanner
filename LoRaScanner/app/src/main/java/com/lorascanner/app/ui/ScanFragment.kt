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

        binding.btnScan.setOnClickListener {
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

        lifecycleScope.launch {
            viewModel.connectionState.collect { state -> updateConnectionUI(state) }
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
        binding.statusIndicator.isActivated = state.isActive
        binding.tvStatus.text = state.label
        binding.btnScan.text = "← Connetti"
        binding.btnSimulate.text = if (state == ConnectionState.RECEIVING) "Stop Sim" else "▶ Demo"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

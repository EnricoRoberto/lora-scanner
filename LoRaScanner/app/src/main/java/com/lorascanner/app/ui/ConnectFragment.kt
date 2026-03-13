package com.lorascanner.app.ui

import android.app.Dialog
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
import com.lorascanner.app.databinding.FragmentConnectBinding
import kotlinx.coroutines.launch

class ConnectFragment : Fragment() {

    private var _binding: FragmentConnectBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScannerViewModel by activityViewModels()
    private lateinit var deviceAdapter: DeviceAdapter
    private var startupDialogShown = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceAdapter = DeviceAdapter { device ->
            viewModel.connect(device)
        }
        binding.recyclerDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDevices.adapter = deviceAdapter

        // Pulsante cerca BLE
        binding.btnScanBle.setOnClickListener {
            viewModel.startScan()
        }

        // Pulsante Demo
        binding.btnDemo.setOnClickListener {
            viewModel.startSimulation()
            // Vai automaticamente alla schermata Scan
            findNavController().navigate(R.id.scanFragment)
        }

        // Pulsante Disconnetti
        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
            viewModel.stopSimulation()
        }

        // Osserva stato connessione
        lifecycleScope.launch {
            viewModel.connectionState.collect { state ->
                updateUI(state)
            }
        }

        // Osserva dispositivi trovati
        lifecycleScope.launch {
            viewModel.discoveredDevices.collect { devices ->
                deviceAdapter.submitList(devices)
                binding.tvFoundLabel.visibility =
                    if (devices.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }

        // Popup automatico all'avvio (solo la prima volta)
        if (!startupDialogShown) {
            startupDialogShown = true
            showStartupDialog()
        }
    }

    private fun showStartupDialog() {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Connetti dispositivo LoRa")
            .setMessage(
                "Per ricevere segnali LoRa serve un modulo Meshtastic via BLE.\n\n" +
                "Dispositivi compatibili:\n" +
                "• LilyGO TTGO T-Beam 868MHz\n" +
                "• Heltec WiFi LoRa 32 V3\n\n" +
                "Se non hai ancora l'hardware usa la Demo Mode."
            )
            .setPositiveButton("🔍 Cerca dispositivi") { _, _ ->
                viewModel.startScan()
            }
            .setNegativeButton("▶ Demo Mode") { _, _ ->
                viewModel.startSimulation()
                findNavController().navigate(R.id.scanFragment)
            }
            .setCancelable(true)
            .create()

        dialog.show()

        // Stile dialog
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(android.graphics.Color.parseColor("#00E5FF"))
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
            ?.setTextColor(android.graphics.Color.parseColor("#FFD740"))
    }

    @SuppressLint("MissingPermission")
    private fun updateUI(state: ConnectionState) {
        binding.statusDot.isActivated = state.isActive
        binding.tvStatus.text = state.label

        when (state) {
            ConnectionState.RECEIVING -> {
                binding.tvDeviceName.text = "Ricezione pacchetti LoRa attiva"
                binding.btnDisconnect.visibility = View.VISIBLE
                binding.btnScanBle.isEnabled = false
                binding.btnDemo.isEnabled = false
            }
            ConnectionState.CONNECTED -> {
                binding.tvDeviceName.text = "Connesso — in attesa di pacchetti…"
                binding.btnDisconnect.visibility = View.VISIBLE
                binding.btnScanBle.isEnabled = false
                binding.btnDemo.isEnabled = false
            }
            ConnectionState.SCANNING -> {
                binding.tvDeviceName.text = "Ricerca in corso…"
                binding.btnDisconnect.visibility = View.GONE
                binding.btnScanBle.isEnabled = false
                binding.btnDemo.isEnabled = true
            }
            ConnectionState.CONNECTING -> {
                binding.tvDeviceName.text = "Connessione in corso…"
                binding.btnDisconnect.visibility = View.GONE
                binding.btnScanBle.isEnabled = false
                binding.btnDemo.isEnabled = false
            }
            else -> {
                binding.tvDeviceName.text = ""
                binding.btnDisconnect.visibility = View.GONE
                binding.btnScanBle.isEnabled = true
                binding.btnDemo.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

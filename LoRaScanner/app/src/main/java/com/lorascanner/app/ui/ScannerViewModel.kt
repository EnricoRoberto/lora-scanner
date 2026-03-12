package com.lorascanner.app.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lorascanner.app.ble.BleManager
import com.lorascanner.app.ble.ConnectionState
import com.lorascanner.app.model.LoRaPacket
import com.lorascanner.app.model.ScanStats
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScannerViewModel(app: Application) : AndroidViewModel(app) {

    val bleManager = BleManager(app)

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = bleManager.discoveredDevices
    val packets: StateFlow<List<LoRaPacket>> = bleManager.packets
    val lastPacket: StateFlow<LoRaPacket?> = bleManager.lastPacket
    val isScanning: StateFlow<Boolean> = bleManager.isScanning

    val stats: StateFlow<ScanStats> = packets.map { list ->
        if (list.isEmpty()) return@map ScanStats()
        val rssiList = list.map { it.rssi }
        val snrList  = list.map { it.snr }
        val now = System.currentTimeMillis()
        val recentCount = list.count { now - it.timestamp.time < 60_000 }
        ScanStats(
            totalPackets = list.size,
            avgRssi = rssiList.average().toFloat(),
            avgSnr  = snrList.average().toFloat(),
            minRssi = rssiList.min(),
            maxRssi = rssiList.max(),
            packetsPerMinute = recentCount.toFloat(),
            uniqueNodes = list.map { it.nodeId }.filter { it.isNotEmpty() }.toSet().size
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, ScanStats())

    // Last N packets for chart
    val recentRssi: StateFlow<List<Float>> = packets.map { list ->
        list.takeLast(60).map { it.rssi.toFloat() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentSnr: StateFlow<List<Float>> = packets.map { list ->
        list.takeLast(60).map { it.snr }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun startScan() = bleManager.startScan()
    fun stopScan()  = bleManager.stopScan()
    fun connect(device: BluetoothDevice) = bleManager.connect(device)
    fun disconnect() = bleManager.disconnect()

    fun startSimulation() = bleManager.startSimulation(viewModelScope)
    fun stopSimulation()  = bleManager.stopSimulation()

    fun clearPackets() {
        // Packets list is managed in BleManager; expose a clear function
        viewModelScope.launch {
            // Reset via reflection on the MutableStateFlow inside BleManager
            // (direct access since same module)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.stopScan()
        bleManager.disconnect()
    }
}

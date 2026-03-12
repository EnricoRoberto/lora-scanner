package com.lorascanner.app.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.util.Log
import com.lorascanner.app.model.LoRaPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Manages BLE connection to a Meshtastic LoRa device.
 * Implements the Meshtastic BLE GATT profile:
 *   Service:  6ba1b218-15a8-461f-9fa8-5d6d1d8b4b7a
 *   FromRadio characteristic: 2c55e69e-4993-11ed-b878-0242ac120002
 *   ToRadio   characteristic: f75c76d2-129e-4dad-a1dd-7866124401e7
 *   FromNum   characteristic: ed9da18c-a800-4f66-a670-aa7547b13685
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"

        // Meshtastic GATT UUIDs
        val MESHTASTIC_SERVICE: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5d6d1d8b4b7a")
        val FROM_RADIO_CHAR:    UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
        val TO_RADIO_CHAR:      UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        val FROM_NUM_CHAR:      UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547b13685")
        val CCCD_UUID:          UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var bleScanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null

    // ─── State flows ────────────────────────────────────────────────────────────
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private val _packets = MutableStateFlow<List<LoRaPacket>>(emptyList())
    val packets: StateFlow<List<LoRaPacket>> = _packets

    private val _lastPacket = MutableStateFlow<LoRaPacket?>(null)
    val lastPacket: StateFlow<LoRaPacket?> = _lastPacket

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val seenDevices = mutableMapOf<String, BluetoothDevice>()

    // ─── BLE Scan ────────────────────────────────────────────────────────────────
    fun startScan() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _connectionState.value = ConnectionState.BT_DISABLED
            return
        }
        bleScanner = adapter.bluetoothLeScanner
        val filter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid(MESHTASTIC_SERVICE))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bleScanner?.startScan(listOf(filter), settings, scanCallback)
        _isScanning.value = true
        _connectionState.value = ConnectionState.SCANNING
        Log.d(TAG, "BLE scan started")
    }

    fun stopScan() {
        bleScanner?.stopScan(scanCallback)
        _isScanning.value = false
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
        Log.d(TAG, "BLE scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!seenDevices.containsKey(device.address)) {
                seenDevices[device.address] = device
                _discoveredDevices.value = seenDevices.values.toList()
                Log.d(TAG, "Found device: ${device.name} (${device.address})")
            }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            _isScanning.value = false
            _connectionState.value = ConnectionState.ERROR("Scan failed: $errorCode")
        }
    }

    // ─── GATT Connection ─────────────────────────────────────────────────────────
    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = ConnectionState.CONNECTING
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected, discovering services…")
                    _connectionState.value = ConnectionState.CONNECTED
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                enableNotifications(gatt)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == FROM_RADIO_CHAR || characteristic.uuid == FROM_NUM_CHAR) {
                parsePacket(value)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            onCharacteristicChanged(gatt, characteristic, characteristic.value ?: return)
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(MESHTASTIC_SERVICE) ?: run {
            Log.e(TAG, "Meshtastic service not found")
            _connectionState.value = ConnectionState.ERROR("Service not found")
            return
        }
        // Enable notifications on FromNum (triggers read of FromRadio)
        val fromNumChar = service.getCharacteristic(FROM_NUM_CHAR)
        gatt.setCharacteristicNotification(fromNumChar, true)
        fromNumChar.getDescriptor(CCCD_UUID)?.let { desc ->
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(desc)
        }
        _connectionState.value = ConnectionState.RECEIVING
        Log.d(TAG, "Notifications enabled")
    }

    // ─── Packet parsing ──────────────────────────────────────────────────────────
    /**
     * Parse raw bytes from Meshtastic BLE.
     * Real implementation would decode protobuf MeshPacket.
     * Here we extract the key RF metadata fields.
     */
    private fun parsePacket(data: ByteArray) {
        if (data.size < 8) return
        try {
            // Meshtastic packet header layout (simplified):
            // Bytes 0-3:  from node id
            // Bytes 4-7:  to node id
            // Byte  8:    channel
            // Byte  9:    hops remaining
            // Bytes 10-11: rx_rssi (int16, dBm)
            // Bytes 12:   rx_snr (int8, 0.25 dB units)
            // Bytes 13:   channel_id
            // Remaining:  payload

            val fromNode = if (data.size >= 4)
                "%08X".format(
                    ((data[0].toInt() and 0xFF) shl 24) or
                    ((data[1].toInt() and 0xFF) shl 16) or
                    ((data[2].toInt() and 0xFF) shl 8) or
                    (data[3].toInt() and 0xFF)
                ) else "UNKNOWN"

            val rssi = if (data.size >= 12)
                ((data[10].toInt() and 0xFF) shl 8 or (data[11].toInt() and 0xFF)).toShort().toInt()
            else (-80 - (10..40).random()) // simulation fallback

            val snrRaw = if (data.size >= 13) data[12].toInt() else (0..40).random()
            val snr = snrRaw * 0.25f

            val hops = if (data.size >= 10) data[9].toInt() and 0x07 else 0

            val payloadHex = if (data.size > 14)
                data.drop(14).take(16).joinToString("") { "%02X".format(it) }
            else data.joinToString("") { "%02X".format(it) }

            val packet = LoRaPacket(
                rssi = rssi.coerceIn(-130, -30),
                snr = snr.coerceIn(-20f, 15f),
                frequency = 868.1f, // EU868 default
                spreadingFactor = 11,
                bandwidth = 250,
                payload = payloadHex,
                nodeId = "!$fromNode",
                hops = hops,
                channel = "LongFast"
            )

            val updated = (_packets.value + packet).takeLast(500)
            _packets.value = updated
            _lastPacket.value = packet
            Log.d(TAG, "Packet: RSSI=${packet.rssi} SNR=${packet.snr} from=${packet.nodeId}")
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    // ─── Simulation mode (for testing without hardware) ──────────────────────────
    private var simJob: kotlinx.coroutines.Job? = null

    fun startSimulation(scope: kotlinx.coroutines.CoroutineScope) {
        _connectionState.value = ConnectionState.RECEIVING
        simJob = scope.launch {
            var counter = 0
            val nodes = listOf("!a1b2c3d4", "!e5f6a7b8", "!c9d0e1f2", "!12345678")
            while (true) {
                kotlinx.coroutines.delay(1500L + (0..2000L).random())
                val baseRssi = -75 - counter % 30
                val packet = LoRaPacket(
                    rssi = baseRssi + (-5..5).random(),
                    snr = 8.5f + (-3..3).random() * 0.5f,
                    frequency = listOf(868.1f, 868.3f, 867.1f, 867.5f).random(),
                    spreadingFactor = listOf(7, 9, 11, 12).random(),
                    bandwidth = 250,
                    payload = (1..8).map { (0..255).random() }
                        .joinToString("") { "%02X".format(it) },
                    nodeId = nodes.random(),
                    hops = (0..3).random(),
                    channel = "LongFast"
                )
                val updated = (_packets.value + packet).takeLast(500)
                _packets.value = updated
                _lastPacket.value = packet
                counter++
            }
        }
    }

    fun stopSimulation() {
        simJob?.cancel()
        simJob = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun IntRange.random() = (first + (Math.random() * (last - first + 1)).toInt())
    private fun LongRange.random() = (first + (Math.random() * (last - first + 1)).toLong())
}

sealed class ConnectionState {
    object DISCONNECTED : ConnectionState()
    object BT_DISABLED  : ConnectionState()
    object SCANNING     : ConnectionState()
    object CONNECTING   : ConnectionState()
    object CONNECTED    : ConnectionState()
    object RECEIVING    : ConnectionState()
    data class ERROR(val message: String) : ConnectionState()

    val label: String get() = when (this) {
        DISCONNECTED -> "Disconnected"
        BT_DISABLED  -> "Bluetooth Off"
        SCANNING     -> "Scanning…"
        CONNECTING   -> "Connecting…"
        CONNECTED    -> "Connected"
        RECEIVING    -> "Receiving"
        is ERROR     -> "Error"
    }
    val isActive: Boolean get() = this == RECEIVING || this == CONNECTED
}

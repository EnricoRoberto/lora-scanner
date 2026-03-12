package com.lorascanner.app.model

import java.util.Date

data class LoRaPacket(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Date = Date(),
    val rssi: Int,           // dBm, e.g. -85
    val snr: Float,          // dB,  e.g. 7.5
    val frequency: Float,    // MHz, e.g. 868.1
    val spreadingFactor: Int = 0,  // SF7..SF12
    val bandwidth: Int = 0,        // kHz: 125, 250, 500
    val payload: String = "",      // hex string
    val nodeId: String = "",       // sender node ID
    val hops: Int = 0,
    val channel: String = ""
) {
    val signalQuality: SignalQuality
        get() = when {
            rssi >= -70 && snr >= 10f -> SignalQuality.EXCELLENT
            rssi >= -85 && snr >= 5f  -> SignalQuality.GOOD
            rssi >= -100 && snr >= 0f -> SignalQuality.FAIR
            else                       -> SignalQuality.POOR
        }
}

enum class SignalQuality(val label: String, val colorHex: String) {
    EXCELLENT("Excellent", "#00E676"),
    GOOD("Good",      "#69F0AE"),
    FAIR("Fair",      "#FFD740"),
    POOR("Poor",      "#FF5252")
}

data class ScanStats(
    val totalPackets: Int = 0,
    val avgRssi: Float = 0f,
    val avgSnr: Float = 0f,
    val minRssi: Int = 0,
    val maxRssi: Int = 0,
    val packetsPerMinute: Float = 0f,
    val uniqueNodes: Int = 0
)

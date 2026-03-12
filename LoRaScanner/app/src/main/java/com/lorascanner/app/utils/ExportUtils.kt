package com.lorascanner.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.lorascanner.app.model.LoRaPacket
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val isoFormat  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())

    fun exportCsv(context: Context, packets: List<LoRaPacket>) {
        if (packets.isEmpty()) {
            Toast.makeText(context, "No packets to export", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val filename = "lora_scan_${dateFormat.format(Date())}.csv"
            val file = File(context.cacheDir, filename)

            val sb = StringBuilder()
            sb.appendLine("timestamp,node_id,rssi_dbm,snr_db,frequency_mhz,spreading_factor,bandwidth_khz,hops,channel,payload,quality")
            packets.forEach { p ->
                sb.appendLine(
                    "${isoFormat.format(p.timestamp)}," +
                    "${p.nodeId}," +
                    "${p.rssi}," +
                    "${"%.2f".format(p.snr)}," +
                    "${"%.1f".format(p.frequency)}," +
                    "${p.spreadingFactor}," +
                    "${p.bandwidth}," +
                    "${p.hops}," +
                    "${p.channel}," +
                    "${p.payload}," +
                    "${p.signalQuality.label}"
                )
            }
            file.writeText(sb.toString())
            shareFile(context, file, "text/csv", filename)
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportJson(context: Context, packets: List<LoRaPacket>) {
        if (packets.isEmpty()) {
            Toast.makeText(context, "No packets to export", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val filename = "lora_scan_${dateFormat.format(Date())}.json"
            val file = File(context.cacheDir, filename)

            val sb = StringBuilder("[\n")
            packets.forEachIndexed { idx, p ->
                sb.append("""
  {
    "timestamp": "${isoFormat.format(p.timestamp)}",
    "node_id": "${p.nodeId}",
    "rssi": ${p.rssi},
    "snr": ${"%.2f".format(p.snr)},
    "frequency": ${"%.1f".format(p.frequency)},
    "sf": ${p.spreadingFactor},
    "bw": ${p.bandwidth},
    "hops": ${p.hops},
    "channel": "${p.channel}",
    "payload": "${p.payload}",
    "quality": "${p.signalQuality.label}"
  }""")
                if (idx < packets.lastIndex) sb.append(",")
                sb.append("\n")
            }
            sb.append("]")
            file.writeText(sb.toString())
            shareFile(context, file, "application/json", filename)
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareFile(context: Context, file: File, mimeType: String, filename: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "LoRa Scan Export - $filename")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export $filename"))
    }
}

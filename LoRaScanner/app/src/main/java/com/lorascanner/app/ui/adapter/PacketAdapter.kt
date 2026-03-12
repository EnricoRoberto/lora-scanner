package com.lorascanner.app.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lorascanner.app.databinding.ItemPacketBinding
import com.lorascanner.app.model.LoRaPacket
import java.text.SimpleDateFormat
import java.util.Locale

class PacketAdapter : ListAdapter<LoRaPacket, PacketAdapter.VH>(DIFF) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPacketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemPacketBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: LoRaPacket) {
            b.tvTime.text    = timeFormat.format(p.timestamp)
            b.tvNodeId.text  = p.nodeId.ifEmpty { "Unknown" }
            b.tvRssi.text    = "${p.rssi} dBm"
            b.tvSnr.text     = "${"%.1f".format(p.snr)} dB"
            b.tvFreq.text    = "${"%.1f".format(p.frequency)} MHz"
            b.tvPayload.text = p.payload.chunked(2).take(8).joinToString(" ")
            b.tvHops.text    = "Hops: ${p.hops}"

            val qualityColor = Color.parseColor(p.signalQuality.colorHex)
            b.signalBar.setBackgroundColor(qualityColor)
            b.tvQuality.text = p.signalQuality.label
            b.tvQuality.setTextColor(qualityColor)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<LoRaPacket>() {
            override fun areItemsTheSame(a: LoRaPacket, b: LoRaPacket) = a.id == b.id
            override fun areContentsTheSame(a: LoRaPacket, b: LoRaPacket) = a == b
        }
    }
}

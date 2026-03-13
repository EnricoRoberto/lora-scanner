package com.lorascanner.app.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.lorascanner.app.databinding.FragmentChartBinding
import kotlinx.coroutines.launch

class ChartFragment : Fragment() {

    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScannerViewModel by activityViewModels()

    private val rssiEntries = mutableListOf<Entry>()
    private val snrEntries  = mutableListOf<Entry>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRssiChart()
        setupSnrChart()
        setupFreqChart()

        lifecycleScope.launch {
            viewModel.packets.collect { packets ->
                val last60 = packets.takeLast(60)

                // Update RSSI chart
                rssiEntries.clear()
                last60.forEachIndexed { i, p -> rssiEntries.add(Entry(i.toFloat(), p.rssi.toFloat())) }
                binding.chartRssi.data?.getDataSetByIndex(0)?.let {
                    (it as LineDataSet).values = rssiEntries
                    binding.chartRssi.data.notifyDataChanged()
                    binding.chartRssi.notifyDataSetChanged()
                    binding.chartRssi.invalidate()
                }

                // Update SNR chart
                snrEntries.clear()
                last60.forEachIndexed { i, p -> snrEntries.add(Entry(i.toFloat(), p.snr)) }
                binding.chartSnr.data?.getDataSetByIndex(0)?.let {
                    (it as LineDataSet).values = snrEntries
                    binding.chartSnr.data.notifyDataChanged()
                    binding.chartSnr.notifyDataSetChanged()
                    binding.chartSnr.invalidate()
                }

                // Frequency histogram
                val freqMap = last60.groupBy { "%.1f".format(it.frequency) }
                    .mapValues { it.value.size }
                updateFreqBars(freqMap)
            }
        }
    }

    private fun setupRssiChart() {
        val chart = binding.chartRssi
        styleLineChart(chart, "RSSI (dBm)", -130f, -30f)

        // Add limit lines
        val good = LimitLine(-85f, "Good").apply {
            lineColor = Color.parseColor("#69F0AE")
            lineWidth = 1.5f
            textColor = Color.parseColor("#69F0AE")
            textSize = 9f
        }
        val poor = LimitLine(-100f, "Poor").apply {
            lineColor = Color.parseColor("#FF5252")
            lineWidth = 1.5f
            textColor = Color.parseColor("#FF5252")
            textSize = 9f
        }
        chart.axisLeft.addLimitLine(good)
        chart.axisLeft.addLimitLine(poor)

        val dataSet = LineDataSet(rssiEntries, "RSSI").apply {
            color = Color.parseColor("#00E5FF")
            setCircleColor(Color.parseColor("#00E5FF"))
            lineWidth = 2f
            circleRadius = 2.5f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            fillAlpha = 60
            fillColor = Color.parseColor("#00E5FF")
            setDrawFilled(true)
        }
        chart.data = LineData(dataSet)
    }

    private fun setupSnrChart() {
        val chart = binding.chartSnr
        styleLineChart(chart, "SNR (dB)", -20f, 15f)

        val dataSet = LineDataSet(snrEntries, "SNR").apply {
            color = Color.parseColor("#FFD740")
            setCircleColor(Color.parseColor("#FFD740"))
            lineWidth = 2f
            circleRadius = 2.5f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            fillAlpha = 60
            fillColor = Color.parseColor("#FFD740")
            setDrawFilled(true)
        }
        chart.data = LineData(dataSet)
    }

    private fun setupFreqChart() {
        val chart = binding.chartFreq
        chart.apply {
            description.isEnabled = false
            setBackgroundColor(Color.parseColor("#0D1117"))
            setDrawGridBackground(false)
            xAxis.apply {
                textColor = Color.parseColor("#8B949E")
                gridColor = Color.parseColor("#21262D")
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()}"
                }
            }
            axisLeft.apply {
                textColor = Color.parseColor("#8B949E")
                gridColor = Color.parseColor("#21262D")
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            legend.textColor = Color.WHITE
        }
    }

    private fun updateFreqBars(freqMap: Map<String, Int>) {
        val entries = freqMap.entries.toList().mapIndexed { i, (_, count) ->
            com.github.mikephil.charting.data.BarEntry(i.toFloat(), count.toFloat())
        }
        val labels = freqMap.keys.toList()

        val dataSet = com.github.mikephil.charting.data.BarDataSet(entries, "Packets per Frequency").apply {
            colors = listOf(
                Color.parseColor("#00E5FF"),
                Color.parseColor("#69F0AE"),
                Color.parseColor("#FFD740"),
                Color.parseColor("#FF6E40"),
                Color.parseColor("#E040FB")
            )
            setDrawValues(true)
            valueTextColor = Color.WHITE
            valueTextSize = 10f
        }

        binding.chartFreq.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float) =
                labels.getOrNull(value.toInt()) ?: ""
        }
        binding.chartFreq.data = com.github.mikephil.charting.data.BarData(dataSet)
        binding.chartFreq.data.barWidth = 0.6f
        binding.chartFreq.invalidate()
    }

    private fun styleLineChart(chart: LineChart, label: String, yMin: Float, yMax: Float) {
        chart.apply {
            description.isEnabled = false
            setBackgroundColor(Color.parseColor("#0D1117"))
            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)

            xAxis.apply {
                textColor = Color.parseColor("#8B949E")
                gridColor = Color.parseColor("#21262D")
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawLabels(false)
            }
            axisLeft.apply {
                textColor = Color.parseColor("#8B949E")
                gridColor = Color.parseColor("#21262D")
                axisMinimum = yMin
                axisMaximum = yMax
            }
            axisRight.isEnabled = false
            legend.apply {
                textColor = Color.WHITE
                form = Legend.LegendForm.LINE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

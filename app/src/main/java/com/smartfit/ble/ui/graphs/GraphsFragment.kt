package com.smartfit.ble.ui.graphs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.smartfit.ble.R
import com.smartfit.ble.SmartFitApp
import com.smartfit.ble.databinding.FragmentGraphsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class GraphsFragment : Fragment() {

    private var _binding: FragmentGraphsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GraphsViewModel by viewModels {
        GraphsViewModelFactory((requireActivity().application as SmartFitApp).appContainer.healthRepository)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGraphsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart(binding.chartHeartRate)
        setupChart(binding.chartBloodPressure)
        observeViewModel()
    }

    private fun setupChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setDragEnabled(true)
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)

        chart.extraBottomOffset = 30f

        val isDarkMode =
            resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

        val textColor = if (isDarkMode) {
            requireContext().getColor(android.R.color.white)
        } else {
            requireContext().getColor(R.color.smartfit_primary_dark)
        }

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.labelRotationAngle = 90f
        xAxis.granularity = 1f
        xAxis.textColor = textColor
        xAxis.axisLineColor = textColor
        xAxis.setDrawGridLines(false)

        chart.axisLeft.setDrawGridLines(false)
        chart.axisLeft.textColor = textColor
        chart.axisLeft.axisLineColor = textColor

        chart.axisRight.isEnabled = false

        chart.legend.isEnabled = true
        chart.legend.textColor = textColor
        chart.legend.textSize = 12f
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.heartRatePoints,
                viewModel.systolicPoints,
                viewModel.diastolicPoints
            ) { hr, sbp, dbp -> Triple(hr, sbp, dbp) }
                .collectLatest { (hr, sbp, dbp) ->

                    if (hr.isEmpty()) {
                        binding.chartHeartRate.visibility = View.GONE
                        binding.chartBloodPressure.visibility = View.GONE
                        binding.tvNoData.visibility = View.VISIBLE
                    } else {
                        binding.chartHeartRate.visibility = View.VISIBLE
                        binding.chartBloodPressure.visibility = View.VISIBLE
                        binding.tvNoData.visibility = View.GONE

                        updateHeartRateChart(hr)
                        updateBloodPressureChart(sbp, dbp)
                    }
                }
        }
    }


    private fun updateHeartRateChart(points: List<ChartPoint>) {

        val timestamps = points.map { it.timestamp }

        binding.chartHeartRate.xAxis.valueFormatter = object : ValueFormatter() {
            private val df = SimpleDateFormat("dd MMM\nhh:mm a", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return if (i in timestamps.indices) df.format(Date(timestamps[i])) else ""
            }
        }

        val entries = points.map { Entry(it.xIndex, it.y) }

        val dataSet = LineDataSet(entries, getString(R.string.heart_rate)).apply {
            color = requireContext().getColor(R.color.smartfit_primary)
            circleRadius = 5f
            lineWidth = 2f

            // draw value above the point
            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor =
                if (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES)
                    requireContext().getColor(android.R.color.white)
                else
                    requireContext().getColor(R.color.smartfit_primary_dark)

            circleColors = points.map {
                when (it.colorType) {
                    0 -> requireContext().getColor(R.color.smartfit_green)       // normal
                    1 -> requireContext().getColor(R.color.smartfit_orange)      // elevated
                    else -> requireContext().getColor(R.color.error_background)  // high
                }
            }
        }

        binding.chartHeartRate.data = LineData(dataSet)
        binding.chartHeartRate.invalidate()
    }



    private fun updateBloodPressureChart(
        systolic: List<ChartPoint>,
        diastolic: List<ChartPoint>
    ) {

        val timestamps = systolic.map { it.timestamp }

        binding.chartBloodPressure.xAxis.valueFormatter = object : ValueFormatter() {
            private val df = SimpleDateFormat("dd MMM\nhh:mm a", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return if (i in timestamps.indices) df.format(Date(timestamps[i])) else ""
            }
        }

        val sysEntries = systolic.map { Entry(it.xIndex, it.y) }
        val diaEntries = diastolic.map { Entry(it.xIndex, it.y) }

        val sysSet = LineDataSet(sysEntries, "Systolic").apply {
            color = requireContext().getColor(R.color.smartfit_primary)
            circleRadius = 5f
            lineWidth = 2f

            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor =
                if (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES)
                    requireContext().getColor(android.R.color.white)
                else
                    requireContext().getColor(R.color.smartfit_primary_dark)

            circleColors = systolic.map {
                when (it.colorType) {
                    0 -> requireContext().getColor(R.color.smartfit_green)
                    1 -> requireContext().getColor(R.color.smartfit_orange)
                    else -> requireContext().getColor(R.color.error_background)
                }
            }
        }

        val diaSet = LineDataSet(diaEntries, "Diastolic").apply {
            color = requireContext().getColor(R.color.smartfit_diastolic_normal)
            circleRadius = 5f
            lineWidth = 2f

            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor =
                if (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES)
                    requireContext().getColor(android.R.color.white)
                else
                    requireContext().getColor(R.color.smartfit_primary_dark)

            circleColors = diastolic.map {
                when (it.colorType) {
                    0 -> requireContext().getColor(R.color.smartfit_green)
                    1 -> requireContext().getColor(R.color.smartfit_orange)
                    else -> requireContext().getColor(R.color.error_background)
                }
            }
        }

        binding.chartBloodPressure.data = LineData(sysSet, diaSet)
        binding.chartBloodPressure.invalidate()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

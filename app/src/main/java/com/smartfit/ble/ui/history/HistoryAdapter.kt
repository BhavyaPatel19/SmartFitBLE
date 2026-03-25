package com.smartfit.ble.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartfit.ble.R
import com.smartfit.ble.data.model.HealthRecord
import com.smartfit.ble.databinding.ItemHistoryCardBinding
import com.smartfit.ble.databinding.ItemHistoryHeaderBinding
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter(
    private val onRecordClick: (HealthRecord) -> Unit
) : ListAdapter<HistoryItem, RecyclerView.ViewHolder>(HistoryDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HistoryItem.Header -> R.layout.item_history_header
            is HistoryItem.Record -> R.layout.item_history_card
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            R.layout.item_history_header -> {
                val binding = ItemHistoryHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                HeaderViewHolder(binding)
            }

            R.layout.item_history_card -> {
                val binding = ItemHistoryCardBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                RecordViewHolder(binding, onRecordClick)
            }

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryItem.Header -> (holder as HeaderViewHolder).bind(item.title)
            is HistoryItem.Record -> (holder as RecordViewHolder).bind(item.record)
        }
    }

    class HeaderViewHolder(private val binding: ItemHistoryHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.tvHeader.text = title
        }
    }

    class RecordViewHolder(
        private val binding: ItemHistoryCardBinding,
        private val onRecordClick: (HealthRecord) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: HealthRecord) {
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

            val patient = buildString {
                append(record.patientName.ifBlank { "Patient" })
                record.patientAge?.let { append(" • ").append(it).append("y") }
            }

            binding.tvPatient.text = patient
            binding.tvDate.text = dateFormat.format(record.date)
            binding.tvTime.text = timeFormat.format(record.date)
            binding.tvHR.text = "${record.heartRate} bpm"
            binding.tvSpo2.text = record.spo2Display
            binding.tvTemperature.text = record.temperatureDisplay

            val context = binding.root.context
            val green = context.getColor(R.color.smartfit_green)
            val orange = context.getColor(R.color.smartfit_orange)
            val red = context.getColor(R.color.error_background)
            val textDefault = context.getColor(R.color.smartfit_primary_dark)

            val hrColor = when {
                record.heartRate in 60..100 -> green
                else -> red
            }
            binding.tvHR.setTextColor(hrColor)

            val spo2Color = when (record.spo2) {
                null -> textDefault
                in 95..100 -> green
                in 90..94 -> orange
                else -> red
            }
            binding.tvSpo2.setTextColor(spo2Color)

            val tempColor = when (record.temperature) {
                null -> textDefault
                in 36.1f..37.2f -> green
                in 35.0f..36.0f, in 37.3f..38.0f -> orange
                else -> red
            }
            binding.tvTemperature.setTextColor(tempColor)

            binding.root.setOnClickListener { onRecordClick(record) }
        }
    }
}

class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
    override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
        return when {
            oldItem is HistoryItem.Header && newItem is HistoryItem.Header ->
                oldItem.title == newItem.title

            oldItem is HistoryItem.Record && newItem is HistoryItem.Record ->
                oldItem.record.id == newItem.record.id

            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
        return oldItem == newItem
    }
}

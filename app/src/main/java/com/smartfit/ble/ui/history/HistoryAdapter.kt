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

            binding.tvDate.text = dateFormat.format(record.date)
            binding.tvTime.text = timeFormat.format(record.date)
            binding.tvHR.text = "${record.heartRate} bpm"
            binding.tvBP.text = record.bloodPressure

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


            val sbp = record.systolicBP
            val dbp = record.diastolicBP

            val bpColor = when {
                sbp < 120 && dbp < 80 -> green
                sbp in 120..129 && dbp < 80 -> orange
                sbp in 130..139 || dbp in 80..89 -> red
                sbp >= 140 || dbp >= 90 -> red
                else -> textDefault
            }
            binding.tvBP.setTextColor(bpColor)

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

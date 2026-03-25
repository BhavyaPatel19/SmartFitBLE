package com.smartfit.ble.ui.history

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.smartfit.ble.R
import com.smartfit.ble.SmartFitApp
import com.smartfit.ble.data.model.HealthRecord
import com.smartfit.ble.databinding.FragmentHistoryBinding
import com.smartfit.ble.ui.addentry.AddEntryDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val appContainer by lazy { (requireActivity().application as SmartFitApp).appContainer }
    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModelFactory(appContainer.healthRepository)
    }

    private lateinit var historyAdapter: HistoryAdapter
    private var latestRecords: List<HealthRecord> = emptyList()
    private var currentQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeToDelete()
        setupSearch()
        observeViewModel()
    }

    private fun setupSearch() {
        binding.etSearchPatient.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString().orEmpty()
                render()
            }
        })
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(::onRecordClicked)

        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter
    }

    private fun onRecordClicked(record: HealthRecord) {
        AddEntryDialogFragment.newInstance(record.id)
            .show(childFragmentManager, "AddEntryDialog")
    }



    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val position = viewHolder.adapterPosition
                val item = historyAdapter.currentList.getOrNull(position)


                return if (item is HistoryItem.Header) 0
                else super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = historyAdapter.currentList[position]

                if (item !is HistoryItem.Record) {
                    historyAdapter.notifyItemChanged(position)
                    return
                }

                val record = item.record


                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.confirm_delete_title))
                    .setMessage(getString(R.string.confirm_delete_message))
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        deleteRecordWithUndo(record)
                    }
                    .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                        historyAdapter.notifyItemChanged(position)
                    }
                    .show()
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.rvHistory)
    }

    private fun deleteRecordWithUndo(record: HealthRecord) {
        viewModel.deleteRecord(record)

        Snackbar.make(binding.root, getString(R.string.entry_deleted), Snackbar.LENGTH_LONG)
            .setAnchorView(requireActivity().findViewById(R.id.bottomNav))
            .setAction(getString(R.string.undo)) {
                lifecycleScope.launch {
                    appContainer.healthRepository.insertRecord(record)
                }
            }
            .show()

    }




    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.records.collectLatest { records ->
                latestRecords = records
                // Delay render to allow Snackbar to appear
                binding.rvHistory.postDelayed({ render() }, 12)
            }
        }
    }

    private fun render() {
        val query = currentQuery.trim().lowercase()
        val filtered = if (query.isBlank()) {
            latestRecords
        } else {
            latestRecords.filter {
                it.patientNameLower.contains(query) || it.patientName.lowercase().contains(query)
            }
        }

        if (filtered.isEmpty()) {
            binding.rvHistory.visibility = View.GONE
            binding.tvEmptyState.visibility = View.VISIBLE
        } else {
            binding.rvHistory.visibility = View.VISIBLE
            binding.tvEmptyState.visibility = View.GONE
            historyAdapter.submitList(groupRecordsByDate(filtered))
        }
    }



    private fun groupRecordsByDate(records: List<HealthRecord>): List<HistoryItem> {
        val calendar = Calendar.getInstance()
        val today = calendar.clone() as Calendar

        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val yesterday = today.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)

        val weekAgo = today.clone() as Calendar
        weekAgo.add(Calendar.DAY_OF_YEAR, -7)

        val items = mutableListOf<HistoryItem>()

        val todayRecords = records.filter {
            Calendar.getInstance().apply { timeInMillis = it.timestamp } >= today
        }

        val yesterdayRecords = records.filter {
            val date = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            date >= yesterday && date < today
        }

        val weekRecords = records.filter {
            val date = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            date >= weekAgo && date < yesterday
        }

        val olderRecords = records.filter {
            Calendar.getInstance().apply { timeInMillis = it.timestamp } < weekAgo
        }

        if (todayRecords.isNotEmpty()) {
            items.add(HistoryItem.Header(getString(R.string.today)))
            todayRecords.forEach { items.add(HistoryItem.Record(it)) }
        }

        if (yesterdayRecords.isNotEmpty()) {
            items.add(HistoryItem.Header(getString(R.string.yesterday)))
            yesterdayRecords.forEach { items.add(HistoryItem.Record(it)) }
        }

        if (weekRecords.isNotEmpty()) {
            items.add(HistoryItem.Header(getString(R.string.previous_7_days)))
            weekRecords.forEach { items.add(HistoryItem.Record(it)) }
        }

        if (olderRecords.isNotEmpty()) {
            items.add(HistoryItem.Header(getString(R.string.older_records)))
            olderRecords.forEach { items.add(HistoryItem.Record(it)) }
        }

        return items
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.ataraxiagoddess.budgetbrewer.ui.spending

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ataraxiagoddess.budgetbrewer.data.SpendingEntry
import com.ataraxiagoddess.budgetbrewer.databinding.ItemTransactionBinding
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyDisplay
import java.text.SimpleDateFormat
import java.util.Locale

class SpendingEntryAdapter(
    private val onEditClick: (SpendingEntry) -> Unit,
    private val onDeleteClick: (SpendingEntry) -> Unit
) : RecyclerView.Adapter<SpendingEntryAdapter.ViewHolder>() {

    private var entries: List<SpendingEntry> = emptyList()
    private val dateFormat = SimpleDateFormat("MM/dd/yy", Locale.US)

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newEntries: List<SpendingEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    override fun getItemCount() = entries.size

    inner class ViewHolder(private val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: SpendingEntry) {
            binding.tvDate.text = dateFormat.format(entry.date)
            binding.tvSource.text = entry.source
            binding.tvAmount.text = entry.amount.toCurrencyDisplay(itemView.resources)

            binding.btnEdit.setOnClickListener { onEditClick(entry) }
            binding.btnDelete.setOnClickListener { onDeleteClick(entry) }
        }
    }
}
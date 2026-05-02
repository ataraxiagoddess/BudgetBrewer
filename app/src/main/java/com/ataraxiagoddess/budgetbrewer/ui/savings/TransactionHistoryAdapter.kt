package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransaction
import com.ataraxiagoddess.budgetbrewer.databinding.ItemTransactionHistoryBinding
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionHistoryAdapter(
    private val transactions: List<SavingsTransaction>,
    private val onEditClick: ((SavingsTransaction) -> Unit)? = null,
    private val onDeleteClick: ((SavingsTransaction) -> Unit)? = null
) : RecyclerView.Adapter<TransactionHistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)

    class ViewHolder(val binding: ItemTransactionHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tx = transactions[position]
        holder.binding.tvDate.text = dateFormat.format(tx.date)
        val amount = tx.amount
        val sign = if (amount >= 0) "+" else "-"
        val color = if (amount >= 0) R.color.net_positive else R.color.net_negative
        holder.binding.tvAmount.text = "${sign}$${String.format("%.2f", Math.abs(amount))}"
        holder.binding.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.context, color))

        // Show edit/delete buttons only when callbacks are provided
        holder.binding.btnEdit.visibility = if (onEditClick != null) View.VISIBLE else View.GONE
        holder.binding.btnDelete.visibility = if (onDeleteClick != null) View.VISIBLE else View.GONE
        holder.binding.btnEdit.setOnClickListener { onEditClick?.invoke(tx) }
        holder.binding.btnDelete.setOnClickListener { onDeleteClick?.invoke(tx) }
    }

    override fun getItemCount() = transactions.size
}
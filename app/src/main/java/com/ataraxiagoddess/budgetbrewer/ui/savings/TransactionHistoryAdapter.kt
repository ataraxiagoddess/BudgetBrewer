/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransaction
import com.ataraxiagoddess.budgetbrewer.databinding.ItemTransactionHistoryBinding
import com.ataraxiagoddess.budgetbrewer.util.CurrencyPrefs
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

class TransactionHistoryAdapter(
    private val onEditClick: ((SavingsTransaction) -> Unit)? = null,
    private val onDeleteClick: ((SavingsTransaction) -> Unit)? = null,
) : ListAdapter<SavingsTransaction, TransactionHistoryAdapter.ViewHolder>(DiffCallback) {
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)

    class ViewHolder(
        val binding: ItemTransactionHistoryBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = ItemTransactionHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val tx = getItem(position)
        holder.binding.tvDate.text = dateFormat.format(tx.date)
        val amount = tx.amount
        val sign = if (amount >= 0) "+" else "-"
        val color = if (amount >= 0) R.color.net_positive else R.color.net_negative
        val formattedAmount = CurrencyPrefs.format(abs(amount), Locale.getDefault())
        holder.binding.tvAmount.text = holder.itemView.context.getString(R.string.amount_signed, sign, formattedAmount)
        holder.binding.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.context, color))

        // Show edit/delete buttons only when callbacks are provided
        holder.binding.btnEdit.visibility = if (onEditClick != null) View.VISIBLE else View.GONE
        holder.binding.btnDelete.visibility = if (onDeleteClick != null) View.VISIBLE else View.GONE
        holder.binding.btnEdit.setOnClickListener { onEditClick?.invoke(tx) }
        holder.binding.btnDelete.setOnClickListener { onDeleteClick?.invoke(tx) }

        val context = holder.binding.root.context
        val dateStr = dateFormat.format(tx.date)
        val signStr = if (amount >= 0) "plus" else "minus"

        val amountStr = String.format(Locale.getDefault(), "%.2f", abs(amount))

        // Conditionally determine the suffix
        val editSuffix = if (onEditClick != null) ", double tap to edit or delete" else ""

        holder.binding.root.contentDescription =
            context.getString(
                R.string.transaction_description,
                dateStr,
                signStr,
                amountStr,
                editSuffix,
            )
    }

    companion object {
        private val DiffCallback =
            object : DiffUtil.ItemCallback<SavingsTransaction>() {
                override fun areItemsTheSame(
                    oldItem: SavingsTransaction,
                    newItem: SavingsTransaction,
                ): Boolean = oldItem.id == newItem.id

                override fun areContentsTheSame(
                    oldItem: SavingsTransaction,
                    newItem: SavingsTransaction,
                ): Boolean = oldItem == newItem
            }
    }
}

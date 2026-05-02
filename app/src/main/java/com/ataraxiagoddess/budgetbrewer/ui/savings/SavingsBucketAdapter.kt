package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucketType
import com.ataraxiagoddess.budgetbrewer.databinding.ItemSavingsBucketBinding

class SavingsBucketAdapter(
    private val onDistributeClick: (SavingsBucket) -> Unit,
    private val onDeductClick: (SavingsBucket) -> Unit,
    private val onWithdrawClick: (SavingsBucket) -> Unit,
    private val onEditClick: (SavingsBucket) -> Unit,
    private val onDeleteClick: (SavingsBucket) -> Unit,
    private val onCardClick: (SavingsBucket) -> Unit
) : ListAdapter<SavingsBucket, SavingsBucketAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSavingsBucketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSavingsBucketBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bucket: SavingsBucket) {
            binding.textViewBucketName.text = bucket.name
            binding.textViewBucketType.text = if (bucket.type == SavingsBucketType.GOAL) "Goal" else "Growth"

            // Actual amount
            binding.textViewActualAmount.text = "$${String.format("%.2f", bucket.current_amount)}"

            // Target amount
            if (bucket.type == SavingsBucketType.GOAL && bucket.target_amount != null) {
                binding.textViewTargetAmount.visibility = View.VISIBLE
                binding.textViewTargetAmount.text = "$${String.format("%.2f", bucket.target_amount)}"
                binding.tvTargetLabel.visibility = View.VISIBLE
            } else {
                binding.textViewTargetAmount.visibility = View.GONE
                binding.tvTargetLabel.visibility = View.GONE
            }

            // Liquid fill logic
            val liquidColor = parseColor(bucket.color_hex)
            val isGoal = bucket.type == SavingsBucketType.GOAL
            val target = bucket.target_amount ?: 0.0
            val current = bucket.current_amount

            val fillFraction: Float
            if (isGoal) {
                fillFraction = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
            } else {
                fillFraction = if (current > 0) 0.5f else 0f
            }

            val liquidDrawable = binding.imageLiquid.drawable
            liquidDrawable.mutate()
            liquidDrawable.setTint(liquidColor)
            val level = (fillFraction * 10000).toInt().coerceIn(0, 10000)
            liquidDrawable.level = level

            // Show withdraw button only when bucket has funds
            binding.btnWithdraw.visibility = if (bucket.current_amount > 0) View.VISIBLE else View.GONE

            // Wire buttons
            binding.btnDistribute.setOnClickListener { onDistributeClick(bucket) }
            binding.btnDeduct.setOnClickListener { onDeductClick(bucket) }
            binding.btnEditBucket.setOnClickListener { onEditClick(bucket) }
            binding.btnWithdraw.setOnClickListener { onWithdrawClick(bucket) }
            binding.btnDeleteBucket.setOnClickListener { onDeleteClick(bucket) }
            binding.cardRoot.setOnClickListener { onCardClick(bucket) }
        }

        private fun parseColor(hex: String): Int {
            return try {
                hex.toColorInt()
            } catch (e: Exception) {
                "#78b4e7".toColorInt()
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<SavingsBucket>() {
        override fun areItemsTheSame(oldItem: SavingsBucket, oldItem2: SavingsBucket): Boolean {
            return oldItem.id == oldItem2.id
        }
        override fun areContentsTheSame(oldItem: SavingsBucket, oldItem2: SavingsBucket): Boolean {
            return oldItem == oldItem2
        }
    }
}
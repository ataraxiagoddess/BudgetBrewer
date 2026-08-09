/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.ui.savings

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

    var transactionCounts: Map<String, Int> = emptyMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSavingsBucketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSavingsBucketBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bucket: SavingsBucket) {
            binding.tvBucketName.text = bucket.name
            binding.tvBucketType.text = if (bucket.type == SavingsBucketType.GOAL) {
                binding.root.context.getString(R.string.goal)
            } else {
                binding.root.context.getString(R.string.growth)
            }
            binding.icBucketType.setImageResource(
                if (bucket.type == SavingsBucketType.GOAL) R.drawable.ic_goal else R.drawable.ic_growth
            )

            // Actual amount
            binding.tvActualAmount.text = binding.root.context.getString(R.string.amount_formatted, bucket.current_amount)

            // Target amount
            if (bucket.type == SavingsBucketType.GOAL && bucket.target_amount != null) {
                binding.tvTargetAmount.visibility = View.VISIBLE
                binding.tvTargetAmount.text = binding.root.context.getString(R.string.amount_formatted, bucket.target_amount)
                binding.tvTargetLabel.visibility = View.VISIBLE
            } else {
                binding.tvTargetAmount.visibility = View.GONE
                binding.tvTargetLabel.visibility = View.GONE
            }

            // Liquid fill logic
            val liquidColor = parseColor(bucket.color_hex)
            val isGoal = bucket.type == SavingsBucketType.GOAL
            val target = bucket.target_amount ?: 0.0
            val current = bucket.current_amount
            val fillFraction: Float = if (isGoal) {
                if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
            } else {
                if (current > 0) 0.5f else 0f
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
            
            val count = transactionCounts[bucket.id] ?: 0
            if (count > 0) {
                binding.cardRoot.isEnabled = true
                binding.cardRoot.isClickable = true
                binding.cardRoot.setOnClickListener { onCardClick(bucket) }
            } else {
                binding.cardRoot.isEnabled = false
                binding.cardRoot.isClickable = false
                binding.cardRoot.setOnClickListener(null)
            }

            val typeStr = if (bucket.type == SavingsBucketType.GOAL) {
                binding.root.context.getString(R.string.goal)
            } else {
                binding.root.context.getString(R.string.growth)
            }
            val currentAmt = binding.root.context.getString(R.string.amount_formatted, bucket.current_amount)
            val goalSuffix = if (bucket.type == SavingsBucketType.GOAL && bucket.target_amount != null) {
                val targetAmt = binding.root.context.getString(R.string.amount_formatted, bucket.target_amount)
                binding.root.context.getString(R.string.goal_suffix, targetAmt)
            } else {
                ""
            }

            val baseDescription = binding.root.context.getString(
                R.string.bucket_description,
                bucket.name,
                typeStr,
                currentAmt,
                goalSuffix
            )
            
            binding.root.contentDescription = if (count > 0) {
                baseDescription
            } else {
                "$baseDescription. No transactions to view."
            }
        }

        private fun parseColor(hex: String): Int {
            return try {
                hex.toColorInt()
            } catch (_: Exception) {
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

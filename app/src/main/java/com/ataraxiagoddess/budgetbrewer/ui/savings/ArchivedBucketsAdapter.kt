/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucketType
import com.ataraxiagoddess.budgetbrewer.databinding.ItemArchivedBucketBinding
import androidx.core.graphics.toColorInt
import com.ataraxiagoddess.budgetbrewer.R

class ArchivedBucketsAdapter(
    private val onRestoreClick: (SavingsBucket) -> Unit,
    private val onDeleteClick: (SavingsBucket) -> Unit,
    private val onCardClick: (SavingsBucket) -> Unit
) : ListAdapter<SavingsBucket, ArchivedBucketsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemArchivedBucketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemArchivedBucketBinding) : RecyclerView.ViewHolder(binding.root) {
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
            binding.tvActualAmount.text = binding.root.context.getString(R.string.amount_formatted, bucket.current_amount)

            if (bucket.type == SavingsBucketType.GOAL && bucket.target_amount != null) {
                binding.tvTargetAmount.visibility = View.VISIBLE
                binding.tvTargetAmount.text =
                    binding.root.context.getString(R.string.amount_formatted, bucket.target_amount)
                binding.tvTargetLabel.visibility = View.VISIBLE
            } else {
                binding.tvTargetAmount.visibility = View.GONE
                binding.tvTargetLabel.visibility = View.GONE
            }

            val liquidColor = parseColor(bucket.color_hex)
            val fillFraction: Float = if (bucket.type == SavingsBucketType.GOAL && bucket.target_amount != null && bucket.target_amount > 0)
                (bucket.current_amount / bucket.target_amount).toFloat().coerceIn(0f, 1f) else if (bucket.current_amount > 0) 0.5f else 0f

            val liquidDrawable = binding.imageLiquid.drawable
            liquidDrawable.mutate()
            liquidDrawable.setTint(liquidColor)
            val level = (fillFraction * 10000).toInt().coerceIn(0, 10000)
            liquidDrawable.level = level

            binding.btnRestore.setOnClickListener { onRestoreClick(bucket) }
            binding.btnDelete.setOnClickListener { onDeleteClick(bucket) }
            binding.cardRoot.setOnClickListener { onCardClick(bucket) }

            val currentAmt = binding.root.context.getString(R.string.amount_formatted, bucket.current_amount)
            val typeStr = if (bucket.type == SavingsBucketType.GOAL) {
                binding.root.context.getString(R.string.goal)
            } else {
                binding.root.context.getString(R.string.growth)
            }
            val goalSuffix = if (bucket.type == SavingsBucketType.GOAL && bucket.target_amount != null) {
                val targetAmt = binding.root.context.getString(R.string.amount_formatted, bucket.target_amount)
                binding.root.context.getString(R.string.goal_suffix, targetAmt)
            } else {
                ""
            }

            binding.root.contentDescription = binding.root.context.getString(
                R.string.bucket_description,
                bucket.name,
                typeStr,
                currentAmt,
                goalSuffix
            )
        }

        private fun parseColor(hex: String) = try { hex.toColorInt() } catch (_: Exception) { "#78b4e7".toColorInt() }
    }

    object DiffCallback : DiffUtil.ItemCallback<SavingsBucket>() {
        override fun areItemsTheSame(oldItem: SavingsBucket, oldItem2: SavingsBucket) = oldItem.id == oldItem2.id
        override fun areContentsTheSame(oldItem: SavingsBucket, oldItem2: SavingsBucket) = oldItem == oldItem2
    }
}

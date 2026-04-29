package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucketType
import com.ataraxiagoddess.budgetbrewer.databinding.ItemSavingsBucketBinding
import androidx.core.graphics.toColorInt

class SavingsBucketAdapter : ListAdapter<SavingsBucket, SavingsBucketAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSavingsBucketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemSavingsBucketBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bucket: SavingsBucket) {
            binding.textViewBucketName.text = bucket.name
            binding.textViewBucketType.text = if (bucket.type == SavingsBucketType.GOAL) "Goal" else "Growth"

            // --- Liquid fill logic ---
            val liquidColor = parseColor(bucket.color_hex)
            val isGoal = bucket.type == SavingsBucketType.GOAL
            val target = bucket.target_amount ?: 0.0
            val current = bucket.current_amount

            val fillFraction: Float
            if (isGoal) {
                fillFraction = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
                // Show "GOAL EXCEEDED" if current > target
                binding.textViewGoalExceeded.visibility = if (current > target && target > 0) View.VISIBLE else View.GONE
            } else { // Growth
                fillFraction = if (current > 0) 0.5f else 0f
                binding.textViewGoalExceeded.visibility = View.GONE
            }

            // Apply tint and clip level to the liquid drawable
            val liquidDrawable = binding.imageLiquid.drawable
            liquidDrawable.mutate()
            liquidDrawable.setTint(liquidColor)

            // Clip level: 0 = empty, 10000 = full
            val level = (fillFraction * 10000).toInt().coerceIn(0, 10000)
            liquidDrawable.level = level

            // Update target amount display for goal buckets
            if (isGoal && bucket.target_amount != null) {
                binding.textViewTargetAmount.visibility = View.VISIBLE
                binding.textViewTargetAmount.text = "$${String.format("%.2f", bucket.target_amount)}"
            } else {
                binding.textViewTargetAmount.visibility = View.GONE
            }
        }

        private fun parseColor(hex: String): Int {
            return try {
                hex.toColorInt()
            } catch (e: Exception) {
                "#78b4e7".toColorInt() // fallback to default blue
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
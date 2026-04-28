package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
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

    inner class ViewHolder(private val binding: ItemSavingsBucketBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bucket: SavingsBucket) {
            binding.textViewBucketName.text = bucket.name
            binding.textViewBucketType.text = if (bucket.type == SavingsBucketType.GOAL) "Goal" else "Growth"

            // Set target amount visibility
            if (bucket.type == SavingsBucketType.GOAL && bucket.target_amount != null) {
                binding.textViewTargetAmount.visibility = View.VISIBLE
                binding.textViewTargetAmount.text = String.format("$%.2f", bucket.target_amount)
            } else {
                binding.textViewTargetAmount.visibility = View.GONE
            }

            // Apply color from hex string
            val color = parseColor(bucket.color_hex)
            (binding.viewColorIndicator.background as? GradientDrawable)?.setColor(color)
                ?: binding.viewColorIndicator.setBackgroundColor(color)
        }
    }

    private fun parseColor(hex: String?): Int {
        return try {
            (hex ?: "#FF6B6B").toColorInt()
        } catch (e: Exception) {
            "#FF6B6B".toColorInt() // fallback
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
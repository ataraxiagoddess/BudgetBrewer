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
            binding.tvBucketType.text = if (bucket.type == SavingsBucketType.GOAL) "Goal" else "Growth"
            binding.icBucketType.setImageResource(
                if (bucket.type == SavingsBucketType.GOAL) R.drawable.ic_goal else R.drawable.ic_growth
            )
            binding.tvActualAmount.text = "$${String.format("%.2f", bucket.current_amount)}"

            if (bucket.type == SavingsBucketType.GOAL && bucket.target_amount != null) {
                binding.tvTargetAmount.visibility = View.VISIBLE
                binding.tvTargetAmount.text = "$${String.format("%.2f", bucket.target_amount)}"
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

            binding.root.contentDescription = buildString {
                append(bucket.name)
                append(", ")
                append(if (bucket.type == SavingsBucketType.GOAL) "goal" else "growth")
                append(", ")
                append("${String.format("%.2f", bucket.current_amount)}")
                if (bucket.type == SavingsBucketType.GOAL && bucket.target_amount != null) {
                    append(" of ")
                    append("${String.format("%.2f", bucket.target_amount)}")
                }
                append(", double tap to view history")
            }
        }

        private fun parseColor(hex: String) = try { hex.toColorInt() } catch (e: Exception) { "#78b4e7".toColorInt() }
    }

    object DiffCallback : DiffUtil.ItemCallback<SavingsBucket>() {
        override fun areItemsTheSame(oldItem: SavingsBucket, oldItem2: SavingsBucket) = oldItem.id == oldItem2.id
        override fun areContentsTheSame(oldItem: SavingsBucket, oldItem2: SavingsBucket) = oldItem == oldItem2
    }
}
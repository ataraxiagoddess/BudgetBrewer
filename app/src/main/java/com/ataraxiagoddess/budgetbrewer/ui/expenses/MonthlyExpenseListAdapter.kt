package com.ataraxiagoddess.budgetbrewer.ui.expenses

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.databinding.ItemMonthlyExpenseDayBinding

class MonthlyExpenseListAdapter(
    private val onCheckboxChanged: (day: Int, isChecked: Boolean) -> Unit
) : ListAdapter<MonthlyExpenseListViewModel.DayExpenses, MonthlyExpenseListAdapter.DayViewHolder>(DayDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).day.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val binding = ItemMonthlyExpenseDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DayViewHolder(binding, onCheckboxChanged)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DayViewHolder(
        private val binding: ItemMonthlyExpenseDayBinding,
        private val onCheckboxChanged: (Int, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(dayExpenses: MonthlyExpenseListViewModel.DayExpenses) {
            binding.tvDayNumber.text = dayExpenses.day.toString()
            binding.checkbox.isChecked = dayExpenses.isChecked

            val context = binding.root.context
            val exoRegular = ResourcesCompat.getFont(context, R.font.exo_regular)
            val exoItalic = ResourcesCompat.getFont(context, R.font.exo_italic)

            if (dayExpenses.expenses.isEmpty()) {
                binding.root.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.bg_container_empty)
                )
                binding.checkbox.visibility = View.INVISIBLE
                binding.tvExpenses.text = ""
                binding.tvExpenses.typeface = ResourcesCompat.getFont(binding.root.context, R.font.exo_regular)
            } else {
                binding.root.setBackgroundResource(R.drawable.category_background)
                binding.checkbox.visibility = View.VISIBLE

                binding.tvExpenses.text = dayExpenses.formattedExpenses

                if (dayExpenses.isChecked) {
                    binding.root.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.bg_container_disabled)
                    )
                    binding.tvExpenses.paint.isStrikeThruText = true
                    binding.tvExpenses.typeface = exoItalic
                } else {
                    binding.tvExpenses.paint.isStrikeThruText = false
                    binding.tvExpenses.typeface = exoRegular
                }
            }

            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                onCheckboxChanged(dayExpenses.day, isChecked)
            }
        }
    }

    class DayDiffCallback : DiffUtil.ItemCallback<MonthlyExpenseListViewModel.DayExpenses>() {
        override fun areItemsTheSame(oldItem: MonthlyExpenseListViewModel.DayExpenses, newItem: MonthlyExpenseListViewModel.DayExpenses): Boolean {
            return oldItem.day == newItem.day
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: MonthlyExpenseListViewModel.DayExpenses, newItem: MonthlyExpenseListViewModel.DayExpenses): Boolean {
            return oldItem.isChecked == newItem.isChecked && oldItem.expenses == newItem.expenses
        }
    }
}
package com.ataraxiagoddess.budgetbrewer.ui.finances

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.Expense
import com.ataraxiagoddess.budgetbrewer.data.ExpenseCategory
import com.ataraxiagoddess.budgetbrewer.data.RecurrenceType
import com.ataraxiagoddess.budgetbrewer.util.SHORT
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyDisplay
import com.google.android.material.button.MaterialButton
import timber.log.Timber
import java.util.Date

class CategoryAdapter(
    private val categories: List<ExpenseCategory>,
    private val allExpenses: List<Expense>,
    private val contentWidth: Int,
    private val halfMargin: Int,
    private val isGrid: Boolean = false,
    private val onEditCategory: (ExpenseCategory) -> Unit,
    private val onDeleteCategory: (ExpenseCategory) -> Unit,
    private val onAddExpense: (ExpenseCategory) -> Unit,
    private val onEditExpense: (Expense) -> Unit,
    private val onDeleteExpense: (Expense) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    override fun getItemCount(): Int = if (isGrid) categories.size else Int.MAX_VALUE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)

        if (contentWidth == ViewGroup.LayoutParams.MATCH_PARENT) {
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        } else {
            val lp = ViewGroup.MarginLayoutParams(contentWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = halfMargin
            lp.rightMargin = halfMargin
            view.layoutParams = lp
        }

        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        if (categories.isEmpty()) return

        val actualPosition = if (isGrid) {
            if (position >= categories.size) {
                Timber.w("CategoryAdapter: Invalid grid position $position, size=${categories.size}. Clamping.")
                categories.size - 1
            } else {
                position
            }
        } else {
            position % categories.size
        }
        holder.bind(categories[actualPosition])
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvCategoryName)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditCategory)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteCategory)
        private val btnAddExpense: MaterialButton = itemView.findViewById(R.id.btnAddExpense)
        private val expensesContainer: LinearLayout = itemView.findViewById(R.id.expensesContainer)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(category: ExpenseCategory) {
            tvName.text = category.name
            btnEdit.setOnClickListener { onEditCategory(category) }
            btnDelete.setOnClickListener { onDeleteCategory(category) }
            btnAddExpense.setOnClickListener { onAddExpense(category) }

            val categoryExpenses = allExpenses
                .filter { it.categoryId == category.id }
                .sortedBy { it.dueDate }

            expensesContainer.removeAllViews()
            categoryExpenses.forEach { expense ->
                val expenseView = createExpenseView(expense)
                expensesContainer.addView(expenseView)
            }
        }

        private fun createExpenseView(expense: Expense): View {
            val context = itemView.context
            val expenseView = LayoutInflater.from(context)
                .inflate(R.layout.item_expense, expensesContainer, false)

            val tvDescription = expenseView.findViewById<TextView>(R.id.tvExpenseDescription)
            val tvAmount = expenseView.findViewById<TextView>(R.id.tvExpenseAmount)
            val tvDueDay = expenseView.findViewById<TextView>(R.id.tvExpenseDueDay)
            val btnEdit = expenseView.findViewById<ImageButton>(R.id.btnEditExpense)
            val btnDelete = expenseView.findViewById<ImageButton>(R.id.btnDeleteExpense)

            tvDescription.text = expense.description
            tvDescription.setTextColor(ContextCompat.getColor(context, R.color.text_on_container))
            tvAmount.text = expense.amount.toCurrencyDisplay(itemView.resources)
            tvDueDay.text = SHORT.format(Date(expense.dueDate))

            if (expense.recurrenceType != RecurrenceType.NONE) {
                val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_recurring, null)
                drawable?.setTint(ContextCompat.getColor(context, R.color.text_on_container))
                tvDescription.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, drawable, null)
            } else {
                tvDescription.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            }

            btnEdit.setOnClickListener { onEditExpense(expense) }
            btnDelete.setOnClickListener { onDeleteExpense(expense) }

            return expenseView
        }
    }
}
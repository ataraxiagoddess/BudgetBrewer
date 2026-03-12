package com.ataraxiagoddess.budgetbrewer.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.Expense
import com.ataraxiagoddess.budgetbrewer.data.Income
import com.ataraxiagoddess.budgetbrewer.data.SpendingEntry
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyDisplay

class DayDetailModalDialogFragment : DialogFragment() {

    private lateinit var dayData: DayData
    private var unassignedIncomes: List<Income> = emptyList()
    private var onAssignIncome: ((Income) -> Unit)? = null
    private var onDeleteIncome: ((Income) -> Unit)? = null

    companion object {
        private const val ARG_DAY = "day"
        private const val ARG_UNASSIGNED = "unassigned"

        fun newInstance(dayData: DayData, unassignedIncomes: List<Income>): DayDetailModalDialogFragment {
            val fragment = DayDetailModalDialogFragment()
            val args = Bundle().apply {
                putSerializable(ARG_DAY, dayData)
                putSerializable(ARG_UNASSIGNED, ArrayList(unassignedIncomes))
            }
            fragment.arguments = args
            return fragment
        }
    }

    data class DayData(
        val dayOfMonth: Int,
        val expenses: List<Expense>,
        val spendingEntries: List<SpendingEntry>,
        val assignedIncomes: List<Income>,
        val dayTotal: Double
    ) : java.io.Serializable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AlertDialogTheme_BudgetBrewer)
        arguments?.let {
            dayData = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                it.getSerializable(ARG_DAY, DayData::class.java) as DayData
            } else {
                @Suppress("DEPRECATION")
                it.getSerializable(ARG_DAY) as DayData
            }
            @Suppress("UNCHECKED_CAST")
            unassignedIncomes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                it.getSerializable(ARG_UNASSIGNED, ArrayList::class.java) as? ArrayList<Income> ?: arrayListOf()
            } else {
                @Suppress("DEPRECATION")
                it.getSerializable(ARG_UNASSIGNED) as? ArrayList<Income> ?: arrayListOf()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_day_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvDayTitle = view.findViewById<TextView>(R.id.tvDayTitle)
        val expensesContainer = view.findViewById<LinearLayout>(R.id.expensesContainer)
        val spendingContainer = view.findViewById<LinearLayout>(R.id.spendingContainer)
        val incomeContainer = view.findViewById<LinearLayout>(R.id.incomeContainer)
        val tvNetTotal = view.findViewById<TextView>(R.id.tvNetTotal)
        val btnAssignIncome = view.findViewById<Button>(R.id.btnAssignIncome)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)

        val exoRegular = ResourcesCompat.getFont(requireContext(), R.font.exo_regular)
        val exoMedium = ResourcesCompat.getFont(requireContext(), R.font.exo_medium)

        tvDayTitle.typeface = exoMedium
        tvNetTotal.typeface = exoMedium
        tvDayTitle.text = getString(R.string.day_x, dayData.dayOfMonth)

        // Close button
        btnClose.setOnClickListener { dismiss() }

        // Expenses
        if (dayData.expenses.isEmpty()) {
            expensesContainer.addView(TextView(requireContext()).apply {
                text = getString(R.string.no_expenses_for_day)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_disabled))
                typeface = exoRegular
            })
        } else {
            dayData.expenses.forEach { expense ->
                expensesContainer.addView(TextView(requireContext()).apply {
                    text = getString(R.string.expense_item_format, expense.description, expense.amount.toCurrencyDisplay(resources))
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_content_text))
                    typeface = exoRegular
                })
            }
        }

        // Spending
        if (dayData.spendingEntries.isEmpty()) {
            spendingContainer.addView(TextView(requireContext()).apply {
                text = getString(R.string.no_spending_for_day)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_disabled))
                typeface = exoRegular
            })
        } else {
            dayData.spendingEntries.forEach { spending ->
                spendingContainer.addView(TextView(requireContext()).apply {
                    text = getString(R.string.spending_item_format, spending.source, spending.amount.toCurrencyDisplay(resources))
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_content_text))
                    typeface = exoRegular
                })
            }
        }

        // Assigned incomes with delete buttons
        if (dayData.assignedIncomes.isEmpty()) {
            incomeContainer.addView(TextView(requireContext()).apply {
                text = getString(R.string.no_income_for_day)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_disabled))
                typeface = exoRegular
            })
        } else {
            dayData.assignedIncomes.forEach { income ->
                val itemView = layoutInflater.inflate(R.layout.item_assigned_income, incomeContainer, false)
                val tvIncomeText = itemView.findViewById<TextView>(R.id.tvIncomeText)
                val btnDelete = itemView.findViewById<ImageButton>(R.id.btnDelete)
                tvIncomeText.text = getString(R.string.income_item_format, income.sourceName, income.amount.toCurrencyDisplay(resources))
                tvIncomeText.typeface = exoRegular
                tvIncomeText.setTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_content_text))
                btnDelete.setOnClickListener {
                    onDeleteIncome?.invoke(income)
                    dismiss()
                }
                incomeContainer.addView(itemView)
            }
        }

        // Net total
        tvNetTotal.text = getString(R.string.net_total, dayData.dayTotal.toCurrencyDisplay(resources))
        when {
            dayData.dayTotal > 0 -> tvNetTotal.setTextColor(ContextCompat.getColor(requireContext(), R.color.net_positive))
            dayData.dayTotal < 0 -> tvNetTotal.setTextColor(ContextCompat.getColor(requireContext(), R.color.net_negative))
            else -> tvNetTotal.setTextColor(ContextCompat.getColor(requireContext(), R.color.net_zero))
        }

        // Assign income button
        if (unassignedIncomes.isNotEmpty()) {
            btnAssignIncome.visibility = View.VISIBLE
            btnAssignIncome.typeface = exoRegular
            btnAssignIncome.setOnClickListener {
                // Show the income selection dialog (existing spinner dialog)
                showIncomeSelectionDialog()
            }
        } else {
            btnAssignIncome.visibility = View.GONE
        }
    }

    private fun showIncomeSelectionDialog() {
        DayDetailDialogFragment.newInstance(
            DayDetailDialogFragment.DayData(
                dayOfMonth = dayData.dayOfMonth,
                expenses = dayData.expenses,
                spendingEntries = dayData.spendingEntries,
                assignedIncomes = dayData.assignedIncomes,
                dayTotal = dayData.dayTotal
            ),
            unassignedIncomes
        ).apply {
            setOnAssignIncomeListener { selectedIncome ->
                onAssignIncome?.invoke(selectedIncome)
                dismiss() // Close the main dialog after assignment
            }
        }.show(childFragmentManager, "IncomeSelectionDialog")
    }

    fun setOnAssignIncomeListener(listener: (Income) -> Unit) {
        onAssignIncome = listener
    }

    fun setOnDeleteIncomeListener(listener: (Income) -> Unit) {
        onDeleteIncome = listener
    }
}
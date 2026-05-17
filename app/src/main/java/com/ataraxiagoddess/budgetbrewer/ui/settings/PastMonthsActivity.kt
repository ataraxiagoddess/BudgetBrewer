package com.ataraxiagoddess.budgetbrewer.ui.settings

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.Budget
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import com.ataraxiagoddess.budgetbrewer.databinding.ActivityPastMonthsBinding
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseActivity
import com.ataraxiagoddess.budgetbrewer.ui.navigation.NavDestination
import com.ataraxiagoddess.budgetbrewer.util.ExportHelper
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyDisplay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class PastMonthsActivity : BaseActivity() {

    override val currentNavDestination: NavDestination
        get() = NavDestination.SETTINGS

    private lateinit var binding: ActivityPastMonthsBinding
    private lateinit var repository: BudgetRepository
    private var pastBudgets: List<Budget> = emptyList()
    private var selectedBudget: Budget? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPastMonthsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideNavigation()

        binding.btnBack.setOnClickListener { finish() }

        val db = AppDatabase.getDatabase(this)
        repository = BudgetRepository(db)

        loadPastMonths()
        setupExportButtons()
    }

    private fun loadPastMonths() {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentYear = calendar.get(Calendar.YEAR)

        lifecycleScope.launch {
            repository.getPastBudgets(currentMonth, currentYear).collect { budgets ->
                pastBudgets = budgets
                if (budgets.isEmpty()) {
                    binding.previewContainer.removeAllViews()
                    val emptyView = TextView(this@PastMonthsActivity).apply {
                        text = getString(R.string.no_past_months)
                        setTextColor(getColor(R.color.text_on_main))
                        textSize = 16f
                        gravity = android.view.Gravity.CENTER
                    }
                    binding.previewContainer.addView(emptyView)
                } else {
                    setupMonthSpinner(budgets)
                }
            }
        }
    }

    private fun setupMonthSpinner(budgets: List<Budget>) {
        val monthNames = budgets.map { "${it.month}/${it.year}" }
        val adapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, monthNames
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.typeface = ResourcesCompat.getFont(this@PastMonthsActivity, R.font.exo_regular)
                view.setTextColor(getColor(R.color.text_on_main))
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.typeface = ResourcesCompat.getFont(this@PastMonthsActivity, R.font.exo_regular)
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPastMonths.adapter = adapter

        binding.spinnerPastMonths.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedBudget = budgets[position]
                loadPreview(budgets[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadPreview(budget: Budget) {
        lifecycleScope.launch {
            binding.previewContainer.removeAllViews()

            val incomes = repository.getIncomesForBudget(budget.id).first()
            val expenses = repository.getExpensesForBudget(budget.id).first()
            val allocation = repository.getAllocationForBudget(budget.id).first()
            val categories = repository.getCategoriesForBudget(budget.id).first()
            val spendingEntries = repository.getSpendingEntriesForBudget(budget.id).first()

            val totalIncome = incomes.sumOf { it.amount }
            val totalExpenses = expenses.sumOf { it.amount }
            val totalSpending = spendingEntries.sumOf { it.amount }

            addSectionLabel("Budget: ${budget.month}/${budget.year}")
            addDataRow("Total Income", totalIncome.toCurrencyDisplay(resources))
            addDataRow("Total Expenses", totalExpenses.toCurrencyDisplay(resources))
            addDataRow("Total Spending", totalSpending.toCurrencyDisplay(resources))
            allocation?.let {
                addDataRow("Savings Allocation", it.savingsAmount.toCurrencyDisplay(resources))
                addDataRow("Spending Allocation", it.spendingAmount.toCurrencyDisplay(resources))
            }
            addDataRow("Categories", categories.size.toString())
            addDataRow("Expenses Count", expenses.size.toString())
            addDataRow("Income Sources", incomes.size.toString())
        }
    }

    private fun addSectionLabel(text: String) {
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.text_on_main))
            textSize = 18f
            typeface = ResourcesCompat.getFont(this@PastMonthsActivity, R.font.exo_semi_bold)
            setPadding(0, 16, 0, 8)
        }
        binding.previewContainer.addView(tv)
    }

    private fun addDataRow(label: String, value: String) {
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
        }
        val exoRegular = ResourcesCompat.getFont(this, R.font.exo_regular)
        val labelTv = TextView(this).apply {
            text = label
            setTextColor(getColor(R.color.text_on_main))
            textSize = 14f
            typeface = exoRegular
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valueTv = TextView(this).apply {
            text = value
            setTextColor(getColor(R.color.text_on_main))
            textSize = 14f
            typeface = exoRegular
        }
        row.addView(labelTv)
        row.addView(valueTv)
        binding.previewContainer.addView(row)
    }

    private fun setupExportButtons() {
        binding.btnExportCSV.setOnClickListener {
            selectedBudget?.let { budget ->
                lifecycleScope.launch {
                    val uri = ExportHelper.exportBudgetToCSV(this@PastMonthsActivity, budget.id, "${budget.month}_${budget.year}")
                    if (uri != null) {
                        showSnackbar(getString(R.string.export_saved))
                    } else {
                        showSnackbar(getString(R.string.export_failed))
                    }
                }
            }
        }
        binding.btnExportPDF.setOnClickListener {
            selectedBudget?.let { budget ->
                lifecycleScope.launch {
                    val uri = ExportHelper.exportBudgetToPDF(this@PastMonthsActivity, budget.id, "${budget.month}_${budget.year}")
                    if (uri != null) {
                        showSnackbar(getString(R.string.export_saved))
                    } else {
                        showSnackbar(getString(R.string.export_failed))
                    }
                }
            }
        }
    }
}
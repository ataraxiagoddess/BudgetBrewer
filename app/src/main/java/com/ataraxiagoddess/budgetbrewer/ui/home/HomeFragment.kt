package com.ataraxiagoddess.budgetbrewer.ui.home

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import com.ataraxiagoddess.budgetbrewer.databinding.FragmentHomeBinding
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseActivity
import com.ataraxiagoddess.budgetbrewer.ui.base.MonthChangeListener
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import com.ataraxiagoddess.budgetbrewer.util.CategoryColors
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyDisplay
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

class HomeFragment : Fragment(), MonthChangeListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var selectedTimeframeButton: MaterialButton? = null

    private lateinit var repository: BudgetRepository

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(repository, requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repository = BudgetRepository(db)

        setupTimeframeToggle()
        setupCharts()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        (activity as? BaseActivity)?.addMonthChangeListener(this)
    }

    override fun onPause() {
        (activity as? BaseActivity)?.removeMonthChangeListener(this)
        super.onPause()
    }

    override fun onMonthChanged(month: Month) {
        Timber.d("HomeFragment onMonthChanged: ${month.getDisplayName(requireContext())}")
        viewModel.updateMonth(month)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getMonthName(month: Int): String {
        return when (month) {
            1 -> getString(R.string.january)
            2 -> getString(R.string.february)
            3 -> getString(R.string.march)
            4 -> getString(R.string.april)
            5 -> getString(R.string.may)
            6 -> getString(R.string.june)
            7 -> getString(R.string.july)
            8 -> getString(R.string.august)
            9 -> getString(R.string.september)
            10 -> getString(R.string.october)
            11 -> getString(R.string.november)
            12 -> getString(R.string.december)
            else -> getString(R.string.unknown)
        }
    }

    private fun setupTimeframeToggle() {
        // Set initial selection to "Last month" (btnTimeframe1m)
        selectedTimeframeButton = binding.btnTimeframe1m
        binding.btnTimeframe1m.isChecked = true

        binding.btnTimeframe1m.setOnClickListener {
            if (it.isSelected) return@setOnClickListener // already selected
            updateButtonSelection(binding.btnTimeframe1m)
            viewModel.setTimeframe(HomeViewModel.Timeframe.ONE_MONTH)
        }

        binding.btnTimeframe3m.setOnClickListener {
            if (it.isSelected) return@setOnClickListener
            updateButtonSelection(binding.btnTimeframe3m)
            viewModel.setTimeframe(HomeViewModel.Timeframe.THREE_MONTHS)
        }

        binding.btnTimeframe6m.setOnClickListener {
            if (it.isSelected) return@setOnClickListener
            updateButtonSelection(binding.btnTimeframe6m)
            viewModel.setTimeframe(HomeViewModel.Timeframe.SIX_MONTHS)
        }

        binding.btnTimeframe1y.setOnClickListener {
            if (it.isSelected) return@setOnClickListener
            updateButtonSelection(binding.btnTimeframe1y)
            viewModel.setTimeframe(HomeViewModel.Timeframe.ONE_YEAR)
        }
    }

    private fun updateButtonSelection(selectedButton: MaterialButton) {
        selectedTimeframeButton?.isChecked = false
        selectedButton.isChecked = true
        selectedTimeframeButton = selectedButton
    }

    private fun setupCharts() {
        binding.chartExpensesBreakdown.apply {
            description.isEnabled = false
            legend.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            holeRadius = 40f
            transparentCircleRadius = 45f
            setDrawEntryLabels(false)
            setUsePercentValues(true)
        }

        binding.chartSpendingTrends.apply {
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(true)
            xAxis.setDrawLabels(true)
            axisLeft.setDrawGridLines(true)
            axisRight.setDrawGridLines(false)
            axisRight.setDrawLabels(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            val context = requireContext()
            val axisTextColor = ContextCompat.getColor(context, R.color.text_on_container)
            val gridColor = ContextCompat.getColor(context, R.color.text_disabled)

            xAxis.textColor = axisTextColor
            axisLeft.textColor = axisTextColor
            axisRight.textColor = axisTextColor

            xAxis.axisLineColor = axisTextColor
            axisLeft.axisLineColor = axisTextColor
            axisRight.axisLineColor = axisTextColor

            xAxis.gridColor = gridColor
            axisLeft.gridColor = gridColor

            // Set custom typeface for axis labels
            val exoRegular = ResourcesCompat.getFont(context, R.font.exo_regular)
            xAxis.typeface = exoRegular
            axisLeft.typeface = exoRegular
            axisRight.typeface = exoRegular

            binding.chartExpensesBreakdown.setNoDataText("")
            binding.chartExpensesBreakdown.setNoDataTextTypeface(exoRegular)

            binding.chartSpendingTrends.setNoDataText("")
            binding.chartSpendingTrends.setNoDataTextTypeface(exoRegular)
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is HomeUiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is HomeUiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        updateIncomeVsExpensesChart(state)
                        updateExpensesBreakdownChart(state)
                        updateSavingsComparisonChart(state)
                        updateSpendingTrendsChart(state)
                        updateSpendingDataList(state)
                    }
                    is HomeUiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Snackbar.make(binding.root,
                            getString(R.string.error_loading_charts),
                            Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updateIncomeVsExpensesChart(data: HomeUiState.Success) {
        val totalIncome = data.totalIncome
        val totalExpenses = data.totalExpenses

        val expensePercentage = if (totalIncome > 0) {
            (totalExpenses / totalIncome * 100).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

        Timber.d("Income: $totalIncome, Expenses: $totalExpenses, Expense %% of Income: $expensePercentage%%")

        binding.incomeCircle.setProgressCompat(100, false)
        binding.expensesRing.setProgressCompat(expensePercentage.toInt(), true)
        binding.tvIncomeCenter.text = totalIncome.toCurrencyDisplay(resources)

        binding.tvExpensesData.text = String.format(Locale.US, "%s: %s (%.1f%%)",
            getString(R.string.expenses),
            totalExpenses.toCurrencyDisplay(resources),
            expensePercentage
        )

        // Apply Exo font to these text views
        applyExoFont(binding.tvIncomeCenter, binding.tvExpensesData)
    }

    private fun updateExpensesBreakdownChart(data: HomeUiState.Success) {
        if (data.expensesByCategory.isEmpty()) {
            binding.chartExpensesBreakdown.clear()
            binding.expensesLegendContainer.removeAllViews()
            val emptyView = TextView(requireContext()).apply {
                text = getString(R.string.no_expense_data)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_container))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                val typeface = ResourcesCompat.getFont(requireContext(), R.font.exo_regular)
                if (typeface == null) {
                    Timber.e("Exo font not found! Using default.")
                }
                this.typeface = typeface ?: Typeface.DEFAULT
            }
            binding.expensesLegendContainer.addView(emptyView)
            return
        }

        val entries = data.expensesByCategory.mapIndexed { index, catExpense ->
            PieEntry(catExpense.amount.toFloat(), index)
        }

        val colors = CategoryColors.colors.map { colorRes ->
            ContextCompat.getColor(requireContext(), colorRes)
        }

        val dataSet = PieDataSet(entries, "").apply {
            sliceSpace = 2f
            selectionShift = 5f
            this.colors = colors
        }

        val pieData = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(binding.chartExpensesBreakdown))
            setValueTextSize(12f)
            setValueTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_container))
            // Set Exo font for the percentage labels on the chart
            setValueTypeface(ResourcesCompat.getFont(requireContext(), R.font.exo_medium))
        }

        binding.chartExpensesBreakdown.data = pieData
        binding.chartExpensesBreakdown.invalidate()

        binding.expensesLegendContainer.removeAllViews()
        val exoRegular = ResourcesCompat.getFont(requireContext(), R.font.exo_regular)
        data.expensesByCategory.forEachIndexed { index, catExpense ->
            val legendRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 4, 0, 4) }
            }

            val colorView = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(16, 16).apply { setMargins(0, 0, 8, 0) }
                setBackgroundColor(colors[index % colors.size])
            }

            val textView = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = getString(
                    R.string.expense_category_format,
                    catExpense.category.name,
                    catExpense.amount.toCurrencyDisplay(resources),
                    catExpense.percentage
                )
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_container))
                textSize = 14f
                typeface = exoRegular
            }

            legendRow.addView(colorView)
            legendRow.addView(textView)
            binding.expensesLegendContainer.addView(legendRow)
        }
    }

    private fun updateSavingsComparisonChart(data: HomeUiState.Success) {
        val targetAmount = data.savingsTarget
        val actualAmount = data.savingsAmount

        val percentageOfGoal = if (targetAmount > 0) {
            (actualAmount / targetAmount * 100).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

        Timber.d("Savings - Target: $targetAmount, Actual: $actualAmount, Percentage: $percentageOfGoal%")

        binding.savingsProgressIndicator.setProgressCompat(percentageOfGoal.toInt(), true)
        binding.tvSavingsPercentage.text = String.format(Locale.US, "%.1f%%", percentageOfGoal)

        binding.tvSavingsData.text = getString(
            R.string.savings_comparison_format,
            getString(R.string.chart_target),
            targetAmount.toCurrencyDisplay(resources),
            getString(R.string.chart_actual),
            actualAmount.toCurrencyDisplay(resources)
        )

        applyExoFont(binding.tvSavingsPercentage, binding.tvSavingsData)
    }

    private fun updateSpendingTrendsChart(data: HomeUiState.Success) {
        val entries = data.spendingHistory.mapIndexed { index, spending ->
            Entry(index.toFloat(), spending.amount.toFloat())
        }

        val dataSet = LineDataSet(entries, getString(R.string.chart_spending)).apply {
            color = ContextCompat.getColor(requireContext(), R.color.chart_trend_line)
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.chart_trend_dot))
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
        }

        val lineData = LineData(dataSet)

        binding.chartSpendingTrends.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index in data.spendingHistory.indices) {
                    getMonthFirstLetter(data.spendingHistory[index].month)
                } else {
                    ""
                }
            }
        }

        binding.chartSpendingTrends.xAxis.setLabelCount(data.spendingHistory.size, true)
        binding.chartSpendingTrends.data = lineData
        binding.chartSpendingTrends.invalidate()
    }

    private fun getMonthFirstLetter(month: Int): String {
        return when (month) {
            1 -> getString(R.string.month_jan_abbr)
            2 -> getString(R.string.month_feb_abbr)
            3 -> getString(R.string.month_mar_abbr)
            4 -> getString(R.string.month_apr_abbr)
            5 -> getString(R.string.month_may_abbr)
            6 -> getString(R.string.month_jun_abbr)
            7 -> getString(R.string.month_jul_abbr)
            8 -> getString(R.string.month_aug_abbr)
            9 -> getString(R.string.month_sep_abbr)
            10 -> getString(R.string.month_oct_abbr)
            11 -> getString(R.string.month_nov_abbr)
            12 -> getString(R.string.month_dec_abbr)
            else -> "?"
        }
    }

    private fun updateSpendingDataList(data: HomeUiState.Success) {
        binding.spendingDataContainer.removeAllViews()

        val exoRegular = ResourcesCompat.getFont(requireContext(), R.font.exo_regular)
        val exoMedium = ResourcesCompat.getFont(requireContext(), R.font.exo_medium)

        data.spendingHistory.forEach { spending ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 4, 0, 4) }
            }

            val monthText = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                text = getString(
                    R.string.month_year_format,
                    getMonthName(spending.month),
                    spending.year
                )
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_container))
                textSize = 14f
                typeface = exoRegular
            }

            val amountText = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = spending.amount.toCurrencyDisplay(resources)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_container))
                textSize = 14f
                setTypeface(exoMedium, Typeface.BOLD)
            }

            row.addView(monthText)
            row.addView(amountText)
            binding.spendingDataContainer.addView(row)
        }
    }

    private fun applyExoFont(vararg views: TextView) {
        val exoRegular = ResourcesCompat.getFont(requireContext(), R.font.exo_regular)
        views.forEach { it.typeface = exoRegular }
    }
}
package com.ataraxiagoddess.budgetbrewer.ui.calendar

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.ataraxiagoddess.budgetbrewer.MainActivity
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import com.ataraxiagoddess.budgetbrewer.databinding.ActivityMonthlyCalendarBinding
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseActivity
import com.ataraxiagoddess.budgetbrewer.ui.base.MonthChangeListener
import com.ataraxiagoddess.budgetbrewer.ui.base.showBudgetBrewerDialog
import com.ataraxiagoddess.budgetbrewer.ui.finances.IncomeExpensesActivity
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import com.ataraxiagoddess.budgetbrewer.ui.navigation.NavDestination
import com.ataraxiagoddess.budgetbrewer.ui.settings.SettingsActivity
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyDisplay
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyEdit
import com.google.android.material.snackbar.Snackbar
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import com.kizitonwose.calendar.core.CalendarDay as LibCalendarDay

class MonthlyCalendarActivity : BaseActivity(), MonthChangeListener {

    override val currentNavDestination: NavDestination
        get() = NavDestination.CALENDAR

    private lateinit var binding: ActivityMonthlyCalendarBinding
    private lateinit var repository: BudgetRepository

    private val viewModel: MonthlyCalendarViewModel by viewModels {
        MonthlyCalendarViewModelFactory(repository, this)
    }

    private val dayNames: List<String> by lazy {
        daysOfWeek(firstDayOfWeek = DayOfWeek.SUNDAY)
            .map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

    inner class DayViewBinder : MonthDayBinder<DayViewContainer> {
        var dayDataMap: Map<LocalDate, MonthlyCalendarViewModel.CalendarDay> = emptyMap()

        override fun create(view: View) = DayViewContainer(view)

        override fun bind(container: DayViewContainer, data: LibCalendarDay) {
            container.bind(data)
        }
    }

    inner class DayViewContainer(view: View) : ViewContainer(view) {
        private val tvDayNumber: TextView? = view.findViewById(R.id.tvDayNumber)
        private val dotContainer: LinearLayout? = view.findViewById(R.id.dotContainer)

        init {
            tvDayNumber?.typeface = ResourcesCompat.getFont(this@MonthlyCalendarActivity, R.font.exo_regular)
        }

        fun bind(calendarDay: LibCalendarDay) {
            try {
                if (tvDayNumber == null) return
                tvDayNumber.text = calendarDay.date.dayOfMonth.toString()

                if (calendarDay.position == DayPosition.MonthDate) {
                    val binder = binding.calendarView.dayBinder as? DayViewBinder
                    val dayData = binder?.dayDataMap?.get(calendarDay.date)
                    if (dayData != null) {
                        updateDayContent(dayData)
                    } else {
                        view.setBackgroundResource(R.drawable.category_background)
                        dotContainer?.removeAllViews()
                    }
                    view.setOnClickListener {
                        dayData?.let { data ->
                            showDayDetailDialog(data)
                        }
                    }
                } else {
                    view.setBackgroundColor(ContextCompat.getColor(this@MonthlyCalendarActivity, android.R.color.transparent))
                    dotContainer?.removeAllViews()
                    view.setOnClickListener(null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error binding day")
            }
        }

        private fun updateDayContent(dayData: MonthlyCalendarViewModel.CalendarDay) {
            view.setBackgroundResource(R.drawable.category_background)
            dotContainer?.removeAllViews()

            if (dayData.expenses.isNotEmpty()) {
                dotContainer?.addView(createDot(R.drawable.circle_expense))
            }
            if (dayData.spendingEntries.isNotEmpty()) {
                dotContainer?.addView(createDot(R.drawable.circle_spending))
            }
            if (dayData.assignedIncomes.isNotEmpty()) {
                dotContainer?.addView(createDot(R.drawable.circle_income))
            }
        }

        private fun createDot(drawableRes: Int): ImageView {
            return ImageView(view.context).apply {
                setImageResource(drawableRes)
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.dot_size),
                    resources.getDimensionPixelSize(R.dimen.dot_size)
                ).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.dot_margin) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonthlyCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.getDatabase(this)
        repository = BudgetRepository(db)

        addMonthChangeListener(this)
        setupCalendar()
        observeData()

        binding.legendContainer.post {
            Timber.d("Legend height: ${binding.legendContainer.height}")
        }
    }

    private fun populateWeekEndGrid(weekEndTotals: List<MonthlyCalendarViewModel.WeekEndTotal>) {
        val container = binding.weekEndGridContainer ?: return
        Timber.d("populateWeekEndGrid: container=$container, weekEndTotals.size=${weekEndTotals.size}")
        container.removeAllViews()

        val exoRegular = ResourcesCompat.getFont(this, R.font.exo_regular)
        val inflater = LayoutInflater.from(this)
        val rowSpacing = resources.getDimensionPixelSize(R.dimen.week_end_row_spacing)
        val pillSpacing = resources.getDimensionPixelSize(R.dimen.pill_horizontal_spacing)

        // Determine if we should use larger text size for tablets in landscape
        val isTablet = resources.getBoolean(R.bool.is_tablet)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val weekPillTextSize = if (isTablet && isLandscape) {
            resources.getDimension(R.dimen.week_pill_text_size_tablet_land) / resources.displayMetrics.density
        } else {
            null
        }

        for (i in weekEndTotals.indices step 2) {
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = rowSpacing }
                orientation = LinearLayout.HORIZONTAL
            }

            // First pill
            val pill1 = inflater.inflate(R.layout.item_week_end_pill, row, false) as LinearLayout
            pill1.findViewById<TextView>(R.id.tvWeek).apply {
                text = getString(R.string.week_x, weekEndTotals[i].weekNumber)
                typeface = exoRegular
                if (weekPillTextSize != null) textSize = weekPillTextSize
            }
            pill1.findViewById<TextView>(R.id.tvAmount).apply {
                text = weekEndTotals[i].total.toCurrencyDisplay(resources)
                typeface = exoRegular
                if (weekPillTextSize != null) textSize = weekPillTextSize
            }
            pill1.setOnClickListener { showSnackbar(getString(R.string.week_end_not_editable)) }
            pill1.layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { setMargins(0, 0, pillSpacing, 0) }
            row.addView(pill1)

            // Second pill or spacer
            if (i + 1 < weekEndTotals.size) {
                val pill2 = inflater.inflate(R.layout.item_week_end_pill, row, false) as LinearLayout
                pill2.findViewById<TextView>(R.id.tvWeek).apply {
                    text = getString(R.string.week_x, weekEndTotals[i + 1].weekNumber)
                    typeface = exoRegular
                    if (weekPillTextSize != null) textSize = weekPillTextSize
                }
                pill2.findViewById<TextView>(R.id.tvAmount).apply {
                    text = weekEndTotals[i + 1].total.toCurrencyDisplay(resources)
                    typeface = exoRegular
                    if (weekPillTextSize != null) textSize = weekPillTextSize
                }
                pill2.setOnClickListener { showSnackbar(getString(R.string.week_end_not_editable)) }
                pill2.layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply { setMargins(pillSpacing, 0, 0, 0) }
                row.addView(pill2)
            } else {
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    ).apply { setMargins(pillSpacing, 0, 0, 0) }
                }
                row.addView(spacer)
            }

            container.addView(row)
        }
    }

    private fun setupCalendar() {
        binding.calendarView.dayBinder = DayViewBinder()
        addDayNamesHeader()
    }

    private fun addDayNamesHeader() {
        val headerContainer = binding.root.findViewById<LinearLayout>(R.id.dayHeader)
        if (headerContainer != null) {
            headerContainer.removeAllViews()
            val exoRegular = ResourcesCompat.getFont(this, R.font.exo_regular)
            dayNames.forEach { dayName ->
                headerContainer.addView(TextView(this).apply {
                    text = dayName
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setTextColor(ContextCompat.getColor(this@MonthlyCalendarActivity, R.color.text_on_main))
                    typeface = exoRegular
                })
            }
        } else {
            Timber.w("No dayHeader found in layout.")
        }
    }

    override fun onMonthChanged(month: Month) {
        viewModel.updateMonth(month)
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is MonthlyCalendarViewModel.CalendarUiState.Loading -> {
                        binding.progressBar?.visibility = View.VISIBLE
                    }
                    is MonthlyCalendarViewModel.CalendarUiState.Success -> {
                        binding.progressBar?.visibility = View.GONE
                        buildNativeHeaderFooter(state.data)
                        loadCalendar(state.data)
                        populateWeekEndGrid(state.data.weekEndTotals)
                    }
                    is MonthlyCalendarViewModel.CalendarUiState.Error -> {
                        binding.progressBar?.visibility = View.GONE
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun loadCalendar(data: MonthlyCalendarViewModel.CalendarData) {
        val yearMonth = YearMonth.of(data.month.year, data.month.month)
        binding.calendarView.setup(yearMonth, yearMonth, firstDayOfWeek = DayOfWeek.SUNDAY)
        binding.calendarView.scrollToMonth(yearMonth)

        val dayDataMap: Map<LocalDate, MonthlyCalendarViewModel.CalendarDay> = data.weeks.flatten()
            .filter { it.isCurrentMonth && it.dayOfMonth != 0 }
            .associateBy(
                keySelector = { day -> LocalDate.of(data.month.year, data.month.month, day.dayOfMonth) },
                valueTransform = { it }
            )

        (binding.calendarView.dayBinder as DayViewBinder).dayDataMap = dayDataMap
        binding.calendarView.notifyCalendarChanged()
    }

    private fun buildNativeHeaderFooter(data: MonthlyCalendarViewModel.CalendarData) {
        // Month Start Amount Row
        binding.monthStartRow.removeAllViews()
        binding.monthStartRow.visibility = View.VISIBLE

        val exoRegular = ResourcesCompat.getFont(this, R.font.exo_regular)
        val monthRowTextSizePx = resources.getDimensionPixelSize(R.dimen.month_row_text_size)
        val density = resources.displayMetrics.density
        val fontScale = resources.configuration.fontScale
        val monthRowTextSizeSp = monthRowTextSizePx / (density * fontScale)

        val endMargin = resources.getDimensionPixelSize(R.dimen.spacing_medium)

        binding.monthStartRow.addView(TextView(this).apply {
            text = getString(R.string.month_start_amount)
            setTextColor(ContextCompat.getColor(context, R.color.text_on_container))
            typeface = exoRegular
            textSize = monthRowTextSizeSp
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = endMargin }
        })

        binding.monthStartRow.addView(TextView(this).apply {
            text = data.monthStartAmount.toCurrencyDisplay(resources)
            setTextColor(ContextCompat.getColor(context, R.color.text_on_container))
            typeface = exoRegular
            textSize = monthRowTextSizeSp
        })

        binding.monthStartRow.setOnClickListener {
            showEditAmountDialog(
                title = if (data.monthStartOverridden)
                    getString(R.string.overwrite_month_start)
                else
                    getString(R.string.edit_month_start),
                currentAmount = data.monthStartAmount,
                isOverridden = data.monthStartOverridden,
                onSave = { newAmount -> viewModel.updateMonthStartAmount(newAmount) }
            )
        }

        // Month End Amount Row
        binding.monthEndRow.removeAllViews()
        binding.monthEndRow.visibility = View.VISIBLE

        val spacingMedium = resources.getDimensionPixelSize(R.dimen.spacing_medium)

        binding.monthEndRow.addView(TextView(this).apply {
            text = getString(R.string.month_end_amount)
            setTextColor(ContextCompat.getColor(context, R.color.text_on_container))
            typeface = exoRegular
            textSize = monthRowTextSizeSp
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = spacingMedium }
        })

        binding.monthEndRow.addView(TextView(this).apply {
            text = data.monthEndAmount.toCurrencyDisplay(resources)
            setTextColor(ContextCompat.getColor(context, R.color.text_on_container))
            typeface = exoRegular
            textSize = monthRowTextSizeSp
        })

        binding.monthEndRow.setOnClickListener {
            showSnackbar(getString(R.string.month_end_not_editable))
        }

        // Week End Totals Row (horizontal container for portrait)
        binding.weekEndContainer?.removeAllViews()
        data.weekEndTotals.forEachIndexed { index, weekEnd ->
            val pill = layoutInflater.inflate(R.layout.item_week_end_pill, binding.weekEndContainer, false) as LinearLayout
            pill.findViewById<TextView>(R.id.tvWeek).apply {
                text = getString(R.string.week_x, index + 1)
                typeface = exoRegular
            }
            pill.findViewById<TextView>(R.id.tvAmount).apply {
                text = weekEnd.total.toCurrencyDisplay(resources)
                typeface = exoRegular
            }
            pill.setOnClickListener {
                showSnackbar(getString(R.string.week_end_not_editable))
            }
            binding.weekEndContainer?.addView(pill)
        }
    }

    private fun showDayDetailDialog(dayData: MonthlyCalendarViewModel.CalendarDay) {
        lifecycleScope.launch {
            val state = viewModel.uiState.value
            if (state is MonthlyCalendarViewModel.CalendarUiState.Success) {
                DayDetailModalDialogFragment.newInstance(
                    DayDetailModalDialogFragment.DayData(
                        dayOfMonth = dayData.dayOfMonth,
                        expenses = dayData.expenses,
                        spendingEntries = dayData.spendingEntries,
                        assignedIncomes = dayData.assignedIncomes,
                        dayTotal = dayData.dayTotal
                    ),
                    state.data.unassignedIncomes
                ).apply {
                    setOnAssignIncomeListener { selectedIncome ->
                        viewModel.assignIncomeToDay(dayData.dayOfMonth, selectedIncome)
                    }
                    setOnDeleteIncomeListener { income ->
                        viewModel.removeIncomeFromDay(income)
                    }
                    show(supportFragmentManager, "DayDetailModalDialog")
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showEditAmountDialog(
        title: String,
        currentAmount: Double,
        isOverridden: Boolean,
        confirmationMessageResId: Int = R.string.overwrite_confirmation,
        onSave: (Double) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_allocation, null, false)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAllocationAmount)
        val tvWarning = dialogView.findViewById<TextView>(R.id.tvAllocationError)

        etAmount.setText(currentAmount.toCurrencyEdit(resources))

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = title,
            view = dialogView,
            positiveButton = getString(R.string.save),
            negativeButton = getString(R.string.cancel)
        )

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.isEnabled = true
            if (isOverridden) {
                tvWarning.text = getString(R.string.overridden_warning)
                tvWarning.visibility = View.VISIBLE
            }

            saveButton.setOnClickListener {
                val newAmount = etAmount.text.toString().toDoubleOrNull()
                if (newAmount != null && newAmount >= 0) {
                    showConfirmationDialog(newAmount, onSave, dialog, confirmationMessageResId)
                }
            }
        }

        dialog.show()
    }

    private fun showConfirmationDialog(
        newAmount: Double,
        onSave: (Double) -> Unit,
        parentDialog: AlertDialog,
        messageResId: Int
    ) {
        val confirmDialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.confirm),
            message = getString(messageResId),
            positiveButton = getString(R.string.yes),
            negativeButton = getString(R.string.no)
        )

        confirmDialog.setOnShowListener {
            confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                onSave(newAmount)
                parentDialog.dismiss()
                confirmDialog.dismiss()
            }
            confirmDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                parentDialog.dismiss()
                confirmDialog.dismiss()
            }
        }

        confirmDialog.show()
    }

    // Navigation overrides
    override fun navigateToHome() {
        startActivity(Intent(this, MainActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }
    override fun navigateToFinances() {
        startActivity(Intent(this, IncomeExpensesActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }
    override fun navigateToExpenses() {
        startActivity(Intent(this, com.ataraxiagoddess.budgetbrewer.ui.expenses.MonthlyExpenseListActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }
    override fun navigateToSpending() {
        startActivity(Intent(this, com.ataraxiagoddess.budgetbrewer.ui.spending.SpendingActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }
    override fun navigateToSettings() {
        startActivity(Intent(this, SettingsActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun onDestroy() {
        removeMonthChangeListener(this)
        super.onDestroy()
    }
}
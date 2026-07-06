package com.ataraxiagoddess.budgetbrewer.ui.finances

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.DatePickerDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.ataraxiagoddess.budgetbrewer.MainActivity
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.AllocationType
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.Expense
import com.ataraxiagoddess.budgetbrewer.data.ExpenseCategory
import com.ataraxiagoddess.budgetbrewer.data.Frequency
import com.ataraxiagoddess.budgetbrewer.data.Income
import com.ataraxiagoddess.budgetbrewer.data.RecurrenceType
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import com.ataraxiagoddess.budgetbrewer.databinding.ActivityIncomeExpensesBinding
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseActivity
import com.ataraxiagoddess.budgetbrewer.ui.base.MonthChangeListener
import com.ataraxiagoddess.budgetbrewer.ui.base.showBudgetBrewerDialog
import com.ataraxiagoddess.budgetbrewer.ui.calendar.MonthlyCalendarActivity
import com.ataraxiagoddess.budgetbrewer.ui.expenses.MonthlyExpenseListActivity
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import com.ataraxiagoddess.budgetbrewer.ui.navigation.NavDestination
import com.ataraxiagoddess.budgetbrewer.ui.savings.SavingsActivity
import com.ataraxiagoddess.budgetbrewer.ui.settings.SettingsActivity
import com.ataraxiagoddess.budgetbrewer.ui.spending.SpendingActivity
import com.ataraxiagoddess.budgetbrewer.util.Constants
import com.ataraxiagoddess.budgetbrewer.util.CurrencyPrefs
import com.ataraxiagoddess.budgetbrewer.util.DecimalDigitsInputFilter
import com.ataraxiagoddess.budgetbrewer.util.FULL
import com.ataraxiagoddess.budgetbrewer.util.toAmountOrNull
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyDisplay
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyEdit
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyFormat
import com.ataraxiagoddess.budgetbrewer.util.toPercentDisplay
import com.ataraxiagoddess.budgetbrewer.util.ValidationUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import kotlin.math.abs

class IncomeExpensesActivity : BaseActivity(), MonthChangeListener {

    override val currentNavDestination: NavDestination
        get() = NavDestination.FINANCES
    private lateinit var binding: ActivityIncomeExpensesBinding
    private lateinit var repository: BudgetRepository
    private val viewModel: IncomeExpensesViewModel by viewModels {
        IncomeExpensesViewModelFactory(repository, this)
    }
    private var previousFrequency: Frequency? = null
    private var isProgrammaticChange = false
    private var isUserFrequencySelection = false
    private var currentMonthFrequency: Frequency = Frequency.MONTHLY
    private var isProgrammaticTipsChange = false
    private var snapHelper: PagerSnapHelper? = null
    private var currentCategoryIndex: Int = 0
    private var categoriesList: List<ExpenseCategory> = emptyList()
    private var expensesList: List<Expense> = emptyList()
    private var previousCategoriesSize: Int = 0
    private val tipEntryHolders = mutableListOf<TipEntryHolder>()

    // Simple data class for income rows
    data class IncomeRow(val timeFrame: String, val weekNumber: Int)

    // ViewHolder for income rows (read‑only)
    class IncomeRowHolder(rowView: View) {
        val tvTimeFrame: TextView = rowView.findViewById(R.id.tvTimeFrame)
        val tvSource: TextView = rowView.findViewById(R.id.tvSource)
        val tvAmount: TextView = rowView.findViewById(R.id.tvAmount)
        val btnAdd: MaterialButton = rowView.findViewById(R.id.btnAddIncome)
        val btnEdit: MaterialButton = rowView.findViewById(R.id.btnEditIncome)
        val btnDelete: MaterialButton = rowView.findViewById(R.id.btnDeleteIncome)
        var weekNumber: Int = 0
        var frequency: Frequency = Frequency.MONTHLY
    }

    class TipEntryHolder(itemView: View) {
        val tvSource: TextView = itemView.findViewById(R.id.tvTipSource)
        val tvAmount: TextView = itemView.findViewById(R.id.tvTipAmount)
        val btnEdit: MaterialButton = itemView.findViewById(R.id.btnEditTip)
        val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDeleteTip)

        var income: Income? = null
    }

    // ==================== HELPER FUNCTIONS ====================

    override fun onMonthChanged(month: Month) {
        super.onMonthChanged(month)
        Timber.d("Month changed to: ${month.getDisplayName(this)}")
        viewModel.updateMonth(month)
    }
    private fun createSimpleTextWatcher(onTextChanged: (s: CharSequence?) -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onTextChanged(s)
            }
            override fun afterTextChanged(s: Editable?) {}
        }
    }


    private fun currencyInputFilters(): Array<InputFilter> {
        val locale = resources.configuration.locales[0]
        return arrayOf(
            DecimalDigitsInputFilter(
                CurrencyPrefs.currentFractionDigits,
                CurrencyPrefs.decimalSeparators(locale)
            )
        )
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun getCurrentFrequency(): Frequency = currentMonthFrequency

    private fun String.toFrequencyOrDefault(): Frequency =
        runCatching { Frequency.valueOf(this) }.getOrDefault(Frequency.MONTHLY)

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        window.attributes.windowAnimations = 0
        super.onCreate(savedInstanceState)

        binding = ActivityIncomeExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvLeftoverAmount.text = 0.0.toCurrencyDisplay(resources)
        // Snap helper for category cards
        snapHelper = PagerSnapHelper()
        snapHelper?.attachToRecyclerView(binding.categoriesRecyclerView)

        // Add scroll listener to track current category
        binding.categoriesRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    snapHelper?.findSnapView(layoutManager)?.let { snapView ->
                        val position = layoutManager.getPosition(snapView)
                        if (categoriesList.isNotEmpty()) {
                            currentCategoryIndex = position % categoriesList.size
                        }
                    }
                }
            }
        })

        binding.categoriesRecyclerView.itemAnimator = null

        val db = AppDatabase.getDatabase(this)
        repository = BudgetRepository(db)

        viewModel.updateMonth(selectedMonth)

        addMonthChangeListener(this)

        setupFrequencySpinner()
        setupAddCategoryButton()
        setupTipsCheckbox()
        observeTips()

        // Observe ViewModel's selected pay frequency to restore spinner on recreation
        lifecycleScope.launch {
            viewModel.selectedPayFrequency.collect { frequency ->
                if (frequency != null) {
                    val frequencies = Frequency.entries.map { it.name }.toTypedArray()
                    val position = frequencies.indexOf(frequency.name).takeIf { it >= 0 } ?: 0
                    if (binding.spinnerFrequency.selectedItemPosition != position) {
                        isProgrammaticChange = true
                        isUserFrequencySelection = false
                        binding.spinnerFrequency.setSelection(position)
                    }
                }
            }
        }

        // Observe month settings (per-month tips and frequency)
        lifecycleScope.launch {
            viewModel.monthSettings.collect { settings ->
                if (settings != null) {
                    // Update Frequency Spinner
                    val frequencies = Frequency.entries.map { it.name }.toTypedArray()
                    val frequency = settings.payFrequency.toFrequencyOrDefault()
                    val position = frequencies.indexOf(frequency.name).takeIf { it >= 0 } ?: 0
                    currentMonthFrequency = frequency
                    previousFrequency = frequency
                    if (binding.spinnerFrequency.selectedItemPosition != position) {
                        isProgrammaticChange = true
                        isUserFrequencySelection = false
                        binding.spinnerFrequency.setSelection(position)
                    }

                    val currentIncomes = (viewModel.uiState.value as? IncomeExpensesUiState.Success)?.incomes ?: emptyList()
                    rebuildIncomeRows(frequency, currentIncomes)

                    if (binding.checkBoxTips.isChecked != settings.tipsEnabled) {
                        isProgrammaticTipsChange = true
                        binding.checkBoxTips.isChecked = settings.tipsEnabled
                    }
                    
                    if (settings.tipsEnabled) {
                        binding.tipsSectionContainer.visibility = View.VISIBLE
                        rebuildTipsGrid(viewModel.tipsList.value)
                    } else {
                        binding.tipsSectionContainer.visibility = View.GONE
                    }
                }
            }
        }

        binding.btnAddTip.setOnClickListener {
            showAddTipDialog()
        }

        // Observe UI events
        lifecycleScope.launch {
            viewModel.event.collect { event ->
                when (event) {
                    // Success messages
                    UiEvent.IncomeAdded -> showSnackbar(getString(R.string.income_added))
                    UiEvent.IncomeUpdated -> showSnackbar(getString(R.string.income_updated))
                    UiEvent.IncomeDeleted -> showSnackbar(getString(R.string.income_deleted))
                    UiEvent.TipAdded -> showSnackbar(getString(R.string.tip_added))
                    UiEvent.TipUpdated -> showSnackbar(getString(R.string.tip_updated))
                    UiEvent.TipDeleted -> showSnackbar(getString(R.string.tip_deleted))
                    UiEvent.CategoryAdded -> showSnackbar(getString(R.string.category_added))
                    UiEvent.CategoryUpdated -> showSnackbar(getString(R.string.category_updated))
                    UiEvent.CategoryDeleted -> showSnackbar(getString(R.string.category_deleted))
                    UiEvent.ExpenseAdded -> showSnackbar(getString(R.string.expense_added))
                    UiEvent.ExpenseUpdated -> showSnackbar(getString(R.string.expense_updated))
                    UiEvent.ExpenseDeleted -> showSnackbar(getString(R.string.expense_deleted))
                    UiEvent.SpendingAdded -> showSnackbar(getString(R.string.spending_added))
                    UiEvent.SpendingUpdated -> showSnackbar(getString(R.string.spending_updated))
                    UiEvent.SpendingDeleted -> showSnackbar(getString(R.string.spending_deleted))

                    // Simple message with just a string resource
                    is UiEvent.ShowMessage -> showSnackbar(getString(event.messageResId))

                    // Error message that might have dynamic content
                    is UiEvent.ShowError -> {
                        val message = if (!event.errorMessage.isNullOrBlank()) {
                            getString(event.errorResId, event.errorMessage)
                        } else {
                            getString(event.errorResId)
                        }
                        showSnackbar(message)
                    }

                    is UiEvent.SavingsAllocationConflict -> {
                        // Show a warning dialog
                        showBudgetBrewerDialog(
                            inflater = layoutInflater,
                            context = this@IncomeExpensesActivity,
                            title = getString(R.string.savings_allocation_conflict_title),
                            message = getString(
                                R.string.savings_allocation_conflict_message,
                                event.distributedTotal.toCurrencyDisplay(resources)
                            ),
                            positiveButton = getString(R.string.ok),
                            negativeButton = null,
                            onPositive = { /* do nothing, just dismiss */ }
                        ).show()
                    }
                }
            }
        }

        // Observe allocation
        lifecycleScope.launch {
            viewModel.allocation.collect {
                updateLeftoverSection()
            }
        }

        // Observe UI state
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is IncomeExpensesUiState.Loading -> {
                        binding.progressBar.isVisible = true
                    }
                    is IncomeExpensesUiState.Success -> {
                        binding.progressBar.isVisible = false
                        rebuildIncomeRows(currentMonthFrequency, state.incomes)
                        updateCategoriesUI(state.categories, state.expenses)
                        updateLeftoverSection()
                    }
                    is IncomeExpensesUiState.Error -> {
                        binding.progressBar.isVisible = false
                        showSnackbar(state.message, Snackbar.LENGTH_LONG)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
    }

    override fun onDestroy() {
        removeMonthChangeListener(this)
        super.onDestroy()
    }

    override fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToSavings() {
        val intent = Intent(this, SavingsActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToExpenses() {
        val intent = Intent(this, MonthlyExpenseListActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToSpending() {
        val intent = Intent(this, SpendingActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToCalendar() {
        val intent = Intent(this, MonthlyCalendarActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToSettings() {
        startActivity(Intent(this, SettingsActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    // ==================== FREQUENCY SPINNER ====================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFrequencySpinner() {
        // 1. Get localized frequency display names from resources
        val frequencyDisplayNames = resources.getStringArray(R.array.frequency_options)
        // Ensure the array order matches Frequency.entries: MONTHLY, WEEKLY, BIWEEKLY
        // so that frequencyDisplayNames[0] = "Monthly" (translated), etc.

        // 2. Adapter with localized strings
        val adapter = object : ArrayAdapter<String>(
            this,
            R.layout.spinner_closed,
            android.R.id.text1,
            frequencyDisplayNames
        ) {
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = layoutInflater.inflate(R.layout.spinner_dropdown_item, parent, false)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.text = getItem(position)
                val divider = view.findViewById<View>(R.id.divider)
                divider.visibility = if (position == count - 1) View.GONE else View.VISIBLE
                return view
            }
        }

        binding.spinnerFrequency.adapter = adapter
        binding.spinnerFrequency.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                isUserFrequencySelection = true
            }
            false
        }

        binding.spinnerFrequency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (isProgrammaticChange) {
                    isProgrammaticChange = false
                    isUserFrequencySelection = false
                    return
                }

                if (!isUserFrequencySelection) {
                    return
                }
                isUserFrequencySelection = false

                // 3. Map the selected position directly to Frequency enum (order matches)
                val newFrequency = Frequency.entries[pos]
                val previous = currentMonthFrequency

                if (previousFrequency == null) {
                    currentMonthFrequency = newFrequency
                    previousFrequency = newFrequency
                    viewModel.updatePayFrequency(newFrequency)
                    viewModel.setSelectedPayFrequency(newFrequency)
                    val currentIncomes = (viewModel.uiState.value as? IncomeExpensesUiState.Success)?.incomes ?: emptyList()
                    rebuildIncomeRows(newFrequency, currentIncomes)
                    return
                }

                if (newFrequency == previous) {
                    val currentIncomes = (viewModel.uiState.value as? IncomeExpensesUiState.Success)?.incomes ?: emptyList()
                    rebuildIncomeRows(newFrequency, currentIncomes)
                    return
                }

                val incomes = (viewModel.uiState.value as? IncomeExpensesUiState.Success)?.incomes ?: emptyList()
                val regularIncomes = incomes.filterNot { it.isTips }
                if (regularIncomes.isEmpty()) {
                    currentMonthFrequency = newFrequency
                    previousFrequency = newFrequency
                    viewModel.updatePayFrequency(newFrequency)
                    viewModel.setSelectedPayFrequency(newFrequency)
                    rebuildIncomeRows(newFrequency, emptyList())
                    return
                }

                val dialog = showBudgetBrewerDialog(
                    inflater = layoutInflater,
                    context = this@IncomeExpensesActivity,
                    title = getString(R.string.change_frequency_title),
                    message = getString(R.string.change_frequency_message),
                    positiveButton = getString(R.string.ok),
                    negativeButton = getString(R.string.cancel)
                )

                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        viewModel.deleteIncomesNotOfFrequency(newFrequency)
                        currentMonthFrequency = newFrequency
                        previousFrequency = newFrequency
                        viewModel.updatePayFrequency(newFrequency)
                        viewModel.setSelectedPayFrequency(newFrequency)
                        rebuildIncomeRows(newFrequency, emptyList())
                        dialog.dismiss()
                    }

                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        isProgrammaticChange = true
                        isUserFrequencySelection = false
                        // 4. Restore previous selection using its display name
                        val previousDisplayName = getFrequencyDisplayName(previous)
                        val previousPosition = frequencyDisplayNames.indexOf(previousDisplayName)
                        binding.spinnerFrequency.setSelection(previousPosition)
                        dialog.dismiss()
                    }
                }

                dialog.show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // 5. Helper to get the localized display name for a Frequency enum
    private fun getFrequencyDisplayName(frequency: Frequency): String {
        val displayNames = resources.getStringArray(R.array.frequency_options)
        return displayNames[frequency.ordinal]
    }

    // ==================== INCOME ROWS ====================

    private fun rebuildIncomeRows(frequency: Frequency, incomes: List<Income>) {
        val rows = when (frequency) {
            Frequency.MONTHLY -> listOf(
                IncomeRow(getString(R.string.monthly_income), 1),
                IncomeRow(getString(R.string.other_income), 2)
            )
            Frequency.BIWEEKLY -> listOf(
                IncomeRow(getString(R.string.weeks_1_2), 1),
                IncomeRow(getString(R.string.weeks_3_4), 2),
                IncomeRow(getString(R.string.week_5_other), 3)
            )
            Frequency.WEEKLY -> listOf(
                IncomeRow(getString(R.string.week_1), 1),
                IncomeRow(getString(R.string.week_2), 2),
                IncomeRow(getString(R.string.week_3), 3),
                IncomeRow(getString(R.string.week_4), 4),
                IncomeRow(getString(R.string.week_5), 5)
            )
        }

        binding.incomeFieldsContainer.removeAllViews()
        rows.forEach { row ->
            val rowView = layoutInflater.inflate(R.layout.item_income_row, binding.incomeFieldsContainer, false)
            val holder = IncomeRowHolder(rowView)
            holder.weekNumber = row.weekNumber
            holder.frequency = frequency
            holder.tvTimeFrame.text = row.timeFrame

            val existing = incomes.find { it.weekNumber == row.weekNumber && it.frequency == frequency }
            if (existing != null) {
                holder.tvSource.text = existing.sourceName
                holder.tvAmount.text = existing.amount.toCurrencyFormat(existing.currency, resources)

                holder.tvSource.visibility = View.VISIBLE
                holder.tvAmount.visibility = View.VISIBLE
                holder.btnAdd.visibility = View.GONE
                holder.btnEdit.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.VISIBLE

                holder.btnEdit.setOnClickListener { showEditIncomeDialog(existing) }
                holder.btnDelete.setOnClickListener { viewModel.deleteIncome(existing) }

                rowView.contentDescription = buildString {
                    append(row.timeFrame)
                    append(", ")
                    append(existing.sourceName)
                    append(", ")
                    append(existing.amount.toCurrencyDisplay(rowView.resources))
                    append(", double tap to edit or delete")
                }
            } else {
                holder.tvSource.visibility = View.GONE
                holder.tvAmount.visibility = View.GONE
                holder.btnAdd.visibility = View.VISIBLE
                holder.btnEdit.visibility = View.GONE
                holder.btnDelete.visibility = View.GONE

                holder.btnAdd.setOnClickListener { showAddIncomeDialog(row.weekNumber) }

                rowView.contentDescription = buildString {
                    append(row.timeFrame)
                    append(", no income added, double tap to add")
                }
            }

            rowView.tag = holder
            binding.incomeFieldsContainer.addView(rowView)
        }
    }

    // ==================== TIPS SECTION ====================

    private fun setupTipsCheckbox() {
        binding.checkBoxTips.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammaticTipsChange) {
                isProgrammaticTipsChange = false // Clear the flag here
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                binding.tipsSectionContainer.visibility = View.VISIBLE
                rebuildTipsGrid(viewModel.tipsList.value)
                viewModel.updateTipsEnabled(true)
            } else {
                val dialog = showBudgetBrewerDialog(
                    inflater = layoutInflater,
                    context = this,
                    title = getString(R.string.tips_misc),
                    message = getString(R.string.tips_delete_warning),
                    positiveButton = getString(R.string.yes),
                    negativeButton = getString(R.string.no),
                    onPositive = { } // Override this in the listener
                )

                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        viewModel.tipsList.value.forEach { viewModel.deleteTip(it) }
                        binding.tipsSectionContainer.visibility = View.GONE
                        tipEntryHolders.clear()
                        viewModel.updateTipsEnabled(false)
                        dialog.dismiss()
                    }

                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        isProgrammaticTipsChange = true
                        binding.checkBoxTips.isChecked = true
                        dialog.dismiss()
                    }
                }

                dialog.show()
            }
        }
    }

    private fun observeTips() {
        lifecycleScope.launch {
            viewModel.tipsList.collect { tips ->
                if (binding.checkBoxTips.isChecked) {
                    rebuildTipsGrid(tips)
                }
            }
        }
    }

    private fun rebuildTipsGrid(tips: List<Income>) {
        binding.tipsContainer.removeAllViews()
        tipEntryHolders.clear()
        tips.forEach { tip ->
            val entryView = layoutInflater.inflate(R.layout.item_tip_entry, binding.tipsContainer, false)
            val holder = TipEntryHolder(entryView)
            holder.income = tip
            holder.tvSource.text = tip.sourceName
            holder.tvAmount.text = tip.amount.toCurrencyFormat(tip.currency, resources)

            holder.btnEdit.setOnClickListener { showEditTipDialog(tip) }
            holder.btnDelete.setOnClickListener {
                showBudgetBrewerDialog(
                    inflater = layoutInflater,
                    context = this@IncomeExpensesActivity,
                    title = getString(R.string.delete_tip_title),
                    message = getString(R.string.delete_tip_confirm),
                    positiveButton = getString(R.string.delete),
                    negativeButton = getString(R.string.cancel),
                    onPositive = { viewModel.deleteTip(tip) }
                ).show()
            }

            entryView.contentDescription = buildString {
                append("Tip, ")
                append(tip.sourceName)
                append(", ")
                append(tip.amount.toCurrencyDisplay(entryView.resources))
                append(", double tap to edit or delete")
            }

            tipEntryHolders.add(holder)
            binding.tipsContainer.addView(entryView)
        }
        updateAddTipButtonVisibility()
    }

    private fun updateAddTipButtonVisibility() {
        binding.btnAddTip.visibility = if (tipEntryHolders.size < Constants.MAX_TIPS && binding.checkBoxTips.isChecked) View.VISIBLE else View.GONE
    }

    // ==================== INCOME DIALOGS ====================

    private fun showAddIncomeDialog(weekNumber: Int) {
        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_income, null,false)
        val etSource = dialogView.findViewById<EditText>(R.id.etIncomeSource)
        val tvSourceCounter = dialogView.findViewById<TextView>(R.id.tvSourceCounter)
        tvSourceCounter.text = getString(R.string.character_counter, 0, ValidationUtils.MAX_LENGTH_NAME)
        val tvAmountError = dialogView.findViewById<TextView>(R.id.tvAmountError)
        etSource.filters = arrayOf(ValidationUtils.getLengthFilter(ValidationUtils.MAX_LENGTH_NAME), ValidationUtils.getControlCharactersBlockFilter())
        val etAmount = dialogView.findViewById<EditText>(R.id.etIncomeAmount)
        etAmount.filters = currencyInputFilters()

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.add_income_dialog_title),
            view = dialogView,
            positiveButton = getString(R.string.add),
            negativeButton = getString(R.string.cancel)
        )

        dialog.setOnShowListener {
            val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            addButton.isEnabled = false

            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val source = etSource.text.toString()
                    tvSourceCounter.text = getString(R.string.character_counter, source.length, ValidationUtils.MAX_LENGTH_NAME)

                    val amount = etAmount.text.toString().toAmountOrNull(resources)
                    val amountValid = amount != null && ValidationUtils.isValidAmount(amount)

                    if (amount != null && !ValidationUtils.isValidAmount(amount)) {
                        tvAmountError.text = getString(R.string.amount_exceeds_maximum)
                        tvAmountError.visibility = View.VISIBLE
                    } else {
                        tvAmountError.visibility = View.INVISIBLE
                    }

                    addButton.isEnabled = source.isNotBlank() && amountValid
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etSource.addTextChangedListener(textWatcher)
            etAmount.addTextChangedListener(textWatcher)

            addButton.setOnClickListener {
                val source = etSource.text.toString().trim()
                val amount = etAmount.text.toString().toAmountOrNull(resources) ?: 0.0
                val frequency = getCurrentFrequency()
                if (!ValidationUtils.isValidName(source)) return@setOnClickListener
                viewModel.addIncome(source, amount, frequency, weekNumber = weekNumber)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showEditIncomeDialog(income: Income) {
        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_income, null, false)
        val etSource = dialogView.findViewById<EditText>(R.id.etIncomeSource)
        val tvSourceCounter = dialogView.findViewById<TextView>(R.id.tvSourceCounter)
        val tvAmountError = dialogView.findViewById<TextView>(R.id.tvAmountError)
        etSource.filters = arrayOf(ValidationUtils.getLengthFilter(ValidationUtils.MAX_LENGTH_NAME), ValidationUtils.getControlCharactersBlockFilter())
        val etAmount = dialogView.findViewById<EditText>(R.id.etIncomeAmount)
        etAmount.filters = currencyInputFilters()
        etSource.setText(ValidationUtils.sanitizeString(income.sourceName))
        tvSourceCounter.text = getString(R.string.character_counter, income.sourceName.length, ValidationUtils.MAX_LENGTH_NAME)
        etAmount.setText(income.amount.toCurrencyEdit(resources))

        val originalSource = income.sourceName
        val originalAmount = income.amount

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.edit_income_dialog_title),
            view = dialogView,
            positiveButton = getString(R.string.save),
            negativeButton = getString(R.string.cancel)
        )

        fun validate() {
            val source = etSource.text.toString().trim()
            val amountText = etAmount.text.toString().trim()
            val amount = amountText.toAmountOrNull(resources) ?: 0.0
            val changed = source != originalSource || amount != originalAmount
            val amountValid = amount > 0 && ValidationUtils.isValidAmount(amount)

            if (amount > 0 && !ValidationUtils.isValidAmount(amount)) {
                tvAmountError.text = getString(R.string.amount_exceeds_maximum)
                tvAmountError.visibility = View.VISIBLE
            } else {
                tvAmountError.visibility = View.INVISIBLE
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = changed && amountValid
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val source = etSource.text.toString()
                tvSourceCounter.text = getString(R.string.character_counter, source.length, ValidationUtils.MAX_LENGTH_NAME)
                validate()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        etSource.addTextChangedListener(textWatcher)
        etAmount.addTextChangedListener(textWatcher)

        dialog.setOnShowListener {
            validate() // initially disabled
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val source = etSource.text.toString().trim()
                val amount = etAmount.text.toString().toAmountOrNull(resources) ?: 0.0
                if (ValidationUtils.isValidName(source) && amount > 0 && ValidationUtils.isValidAmount(amount)) {
                    val updated = income.copy(sourceName = source, amount = amount)
                    viewModel.updateIncome(updated)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    // ==================== TIP DIALOGS ====================

    private fun showAddTipDialog() {
        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_tip, null, false)
        val etSource = dialogView.findViewById<EditText>(R.id.etTipSource)
        val tvSourceCounter = dialogView.findViewById<TextView>(R.id.tvSourceCounter)
        tvSourceCounter.text = getString(R.string.character_counter, 0, ValidationUtils.MAX_LENGTH_NAME)
        val tvAmountError = dialogView.findViewById<TextView>(R.id.tvAmountError)
        etSource.filters = arrayOf(ValidationUtils.getLengthFilter(ValidationUtils.MAX_LENGTH_NAME), ValidationUtils.getControlCharactersBlockFilter())
        val etAmount = dialogView.findViewById<EditText>(R.id.etTipAmount)
        etAmount.filters = currencyInputFilters()

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.add_tip_dialog_title),
            view = dialogView,
            positiveButton = getString(R.string.add),
            negativeButton = getString(R.string.cancel)
        )

        dialog.setOnShowListener {
            val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            addButton.isEnabled = false

            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val source = etSource.text.toString()
                    tvSourceCounter.text = getString(R.string.character_counter, source.length, ValidationUtils.MAX_LENGTH_NAME)

                    val amount = etAmount.text.toString().toAmountOrNull(resources)
                    val amountValid = amount != null && ValidationUtils.isValidAmount(amount)

                    if (amount != null && !ValidationUtils.isValidAmount(amount)) {
                        tvAmountError.text = getString(R.string.amount_exceeds_maximum)
                        tvAmountError.visibility = View.VISIBLE
                    } else {
                        tvAmountError.visibility = View.INVISIBLE
                    }

                    addButton.isEnabled = source.isNotBlank() && amountValid
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etSource.addTextChangedListener(textWatcher)
            etAmount.addTextChangedListener(textWatcher)

            addButton.setOnClickListener {
                val source = etSource.text.toString().trim()
                val amount = etAmount.text.toString().toAmountOrNull(resources) ?: 0.0
                val maxOrder = viewModel.tipsList.value.maxOfOrNull { it.tipsOrder ?: 0 } ?: 0
                if (!ValidationUtils.isValidName(source)) return@setOnClickListener
                viewModel.addTip(source, amount, maxOrder + 1)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showEditTipDialog(tip: Income) {
        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_tip, null, false)
        val etSource = dialogView.findViewById<EditText>(R.id.etTipSource)
        val tvSourceCounter = dialogView.findViewById<TextView>(R.id.tvSourceCounter)
        val tvAmountError = dialogView.findViewById<TextView>(R.id.tvAmountError)
        etSource.filters = arrayOf(ValidationUtils.getLengthFilter(ValidationUtils.MAX_LENGTH_NAME), ValidationUtils.getControlCharactersBlockFilter())
        val etAmount = dialogView.findViewById<EditText>(R.id.etTipAmount)
        etAmount.filters = currencyInputFilters()
        etSource.setText(ValidationUtils.sanitizeString(tip.sourceName))
        tvSourceCounter.text = getString(R.string.character_counter, tip.sourceName.length, ValidationUtils.MAX_LENGTH_NAME)
        etAmount.setText(tip.amount.toCurrencyEdit(resources))

        val originalSource = tip.sourceName
        val originalAmount = tip.amount

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.edit_tip_dialog_title),
            view = dialogView,
            positiveButton = getString(R.string.save),
            negativeButton = getString(R.string.cancel)
        )

        fun validate() {
            val source = etSource.text.toString().trim()
            val amountText = etAmount.text.toString().trim()
            val amount = amountText.toAmountOrNull(resources) ?: 0.0
            val changed = source != originalSource || amount != originalAmount
            val amountValid = amount > 0 && ValidationUtils.isValidAmount(amount)

            if (amount > 0 && !ValidationUtils.isValidAmount(amount)) {
                tvAmountError.text = getString(R.string.amount_exceeds_maximum)
                tvAmountError.visibility = View.VISIBLE
            } else {
                tvAmountError.visibility = View.INVISIBLE
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = changed && amountValid
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val source = etSource.text.toString()
                tvSourceCounter.text = getString(R.string.character_counter, source.length, ValidationUtils.MAX_LENGTH_NAME)
                validate()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        etSource.addTextChangedListener(textWatcher)
        etAmount.addTextChangedListener(textWatcher)

        dialog.setOnShowListener {
            validate()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val source = etSource.text.toString().trim()
                val amount = etAmount.text.toString().toAmountOrNull(resources) ?: 0.0
                if (ValidationUtils.isValidName(source) && amount > 0 && ValidationUtils.isValidAmount(amount)) {
                    val updated = tip.copy(sourceName = source, amount = amount)
                    viewModel.updateTip(updated)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    // ==================== CATEGORY DIALOGS ====================

    private fun setupAddCategoryButton() {
        binding.btnAddCategory.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is IncomeExpensesUiState.Success) {
                if (state.categories.size >= Constants.MAX_CATEGORIES) {
                    showSnackbar(getString(R.string.max_categories_reached, Constants.MAX_CATEGORIES))
            } else {
                    showAddCategoryDialog()
                }
            }
        }
    }

    private fun showAddCategoryDialog() {
        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null, false)
        val etName = dialogView.findViewById<EditText>(R.id.etCategoryName)
        etName.filters = arrayOf(ValidationUtils.getLengthFilter(ValidationUtils.MAX_LENGTH_NAME), ValidationUtils.getControlCharactersBlockFilter())

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.add_category_title),
            view = dialogView,
            positiveButton = getString(R.string.add),
            negativeButton = getString(R.string.cancel)
        )

        dialog.setOnShowListener {
            val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            addButton.isEnabled = false

            etName.addTextChangedListener(createSimpleTextWatcher {
                addButton.isEnabled = !it.isNullOrBlank()
            })

            addButton.setOnClickListener {
                val name = etName.text.toString().trim()
                if (ValidationUtils.isValidName(name)) {
                    viewModel.addCategory(name)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showEditCategoryDialog(category: ExpenseCategory) {
        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null, false)
        val etName = dialogView.findViewById<EditText>(R.id.etCategoryName)
        etName.filters = arrayOf(ValidationUtils.getLengthFilter(ValidationUtils.MAX_LENGTH_NAME), ValidationUtils.getControlCharactersBlockFilter())
        etName.setText(ValidationUtils.sanitizeString(category.name))

        val originalName = category.name

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.edit_category_title),
            view = dialogView,
            positiveButton = getString(R.string.save),
            negativeButton = getString(R.string.cancel)
        )

        fun validate() {
            val newName = etName.text.toString().trim()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = newName.isNotEmpty() && newName != originalName
        }

        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validate() }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialog.setOnShowListener {
            validate()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = etName.text.toString().trim()
                if (ValidationUtils.isValidName(newName)) {
                    val updatedCategory = category.copy(name = newName)
                    viewModel.updateCategory(updatedCategory)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showDeleteCategoryDialog(category: ExpenseCategory) {
        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.delete_category_title),
            message = getString(R.string.delete_category_message, category.name),
            positiveButton = getString(R.string.delete),
            negativeButton = getString(R.string.cancel),
            onPositive = { viewModel.deleteCategory(category) }
        )

        // Need to handle the custom button behavior
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                viewModel.deleteCategory(category)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    // ==================== EXPENSE DIALOGS ====================

    private fun showAddExpenseDialog(category: ExpenseCategory) {
        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_expense, null, false)
        val etDescription = dialogView.findViewById<EditText>(R.id.etExpenseDescription)
        val tvDescriptionCounter = dialogView.findViewById<TextView>(R.id.tvDescriptionCounter)
        tvDescriptionCounter.text = getString(R.string.character_counter, 0, ValidationUtils.MAX_LENGTH_NAME)
        val tvAmountError = dialogView.findViewById<TextView>(R.id.tvAmountError)
        etDescription.filters = arrayOf(ValidationUtils.getLengthFilter(ValidationUtils.MAX_LENGTH_NAME), ValidationUtils.getControlCharactersBlockFilter())
        val etAmount = dialogView.findViewById<EditText>(R.id.etExpenseAmount)
        etAmount.filters = currencyInputFilters()
        val btnSelectDate = dialogView.findViewById<Button>(R.id.btnSelectDate)
        val cbRecurring = dialogView.findViewById<CheckBox>(R.id.cbRecurring)
        val recurrenceOptions = dialogView.findViewById<LinearLayout>(R.id.recurrenceOptions)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)
        val radioMonthly = dialogView.findViewById<RadioButton>(R.id.radioMonthly)
        val radioEveryX = dialogView.findViewById<RadioButton>(R.id.radioEveryX)
        val etEveryXDays = dialogView.findViewById<EditText>(R.id.etEveryXDays)
        val tvRecurrenceError = dialogView.findViewById<TextView>(R.id.tvRecurrenceError)

        etEveryXDays.isEnabled = false
        etEveryXDays.setTextColor(ContextCompat.getColor(this, R.color.text_disabled))
        etEveryXDays.background = ContextCompat.getDrawable(this, R.drawable.edittext_background_everyx)

        etAmount.imeOptions = EditorInfo.IME_ACTION_DONE
        etAmount.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(etAmount)
                true
            } else {
                false
            }
        }

        var selectedDate: Long? = null
        recurrenceOptions.visibility = View.GONE
        etEveryXDays.inputType = InputType.TYPE_CLASS_NUMBER
        etEveryXDays.filters = arrayOf(ValidationUtils.getLengthFilter(3))

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.add_expense_title, category.name),
            view = dialogView,
            positiveButton = getString(R.string.add),
            negativeButton = getString(R.string.cancel)
        )

        fun validateAndEnable() {
            val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE) ?: return
            val description = etDescription.text.toString()
            tvDescriptionCounter.text = getString(R.string.character_counter, description.length, ValidationUtils.MAX_LENGTH_NAME)
            val descriptionValid = !description.isBlank()

            val amount = etAmount.text.toString().toAmountOrNull(resources)
            val amountValid = amount != null && ValidationUtils.isValidAmount(amount)

            if (amount != null && !ValidationUtils.isValidAmount(amount)) {
                tvAmountError.text = getString(R.string.amount_exceeds_maximum)
                tvAmountError.visibility = View.VISIBLE
            } else {
                tvAmountError.visibility = View.INVISIBLE
            }

            val dateValid = selectedDate != null

            var recurrenceValid = true
            if (cbRecurring.isChecked) {
                recurrenceValid = radioMonthly.isChecked || radioEveryX.isChecked
                if (radioEveryX.isChecked) {
                    val daysText = etEveryXDays.text.toString()
                    val days = daysText.toIntOrNull()
                    recurrenceValid = days != null && ValidationUtils.isValidRecurrenceDays(days)

                    if (days != null && !ValidationUtils.isValidRecurrenceDays(days)) {
                        tvRecurrenceError.text = getString(R.string.recurrence_exceeds_maximum)
                        tvRecurrenceError.visibility = View.VISIBLE
                    } else {
                        tvRecurrenceError.visibility = View.INVISIBLE
                    }
                } else {
                    tvRecurrenceError.visibility = View.INVISIBLE
                }
            } else {
                tvRecurrenceError.visibility = View.INVISIBLE
            }

            addButton.isEnabled = descriptionValid && amountValid && dateValid && recurrenceValid
        }

        cbRecurring.setOnCheckedChangeListener { _, isChecked ->
            recurrenceOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                radioMonthly.isChecked = false
                radioEveryX.isChecked = false
                etEveryXDays.text.clear()
            }
            validateAndEnable()
        }

        etDescription.addTextChangedListener(createSimpleTextWatcher { validateAndEnable() })
        etAmount.addTextChangedListener(createSimpleTextWatcher { validateAndEnable() })
        etEveryXDays.addTextChangedListener(createSimpleTextWatcher { validateAndEnable() })

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioEveryX) {
                etEveryXDays.isEnabled = true
                etEveryXDays.setTextColor(ContextCompat.getColor(this, R.color.text_on_container))
            } else {
                etEveryXDays.isEnabled = false
                etEveryXDays.setTextColor(ContextCompat.getColor(this, R.color.text_disabled))
                etEveryXDays.text.clear()
            }
            validateAndEnable()
        }

        btnSelectDate.setOnClickListener {
            hideKeyboard(btnSelectDate)
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                R.style.ThemeOverlay_BudgetBrewer_DatePicker,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDate = calendar.timeInMillis
                    btnSelectDate.text = Constants.DateFormats.FULL.format(calendar.time)
                    validateAndEnable()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        dialog.setOnShowListener {
            validateAndEnable()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val description = etDescription.text.toString().trim()
                val amount = etAmount.text.toString().toAmountOrNull(resources) ?: 0.0
                val date = selectedDate
                if (!ValidationUtils.isValidName(description)) return@setOnClickListener
                if (date != null && description.isNotEmpty()) {
                    val recurrenceType = if (cbRecurring.isChecked) {
                        when {
                            radioMonthly.isChecked -> RecurrenceType.MONTHLY_SAME_DAY
                            radioEveryX.isChecked -> RecurrenceType.EVERY_X_DAYS
                            else -> RecurrenceType.NONE
                        }
                    } else RecurrenceType.NONE

                    val interval = if (recurrenceType == RecurrenceType.EVERY_X_DAYS) {
                        etEveryXDays.text.toString().toIntOrNull()
                    } else null

                    viewModel.addExpense(category.id, description, amount, date, recurrenceType, interval)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showEditExpenseDialog(expense: Expense) {
        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_expense, null, false)
        val etDescription = dialogView.findViewById<EditText>(R.id.etExpenseDescription)
        val tvDescriptionCounter = dialogView.findViewById<TextView>(R.id.tvDescriptionCounter)
        val tvAmountError = dialogView.findViewById<TextView>(R.id.tvAmountError)
        val tvRecurrenceError = dialogView.findViewById<TextView>(R.id.tvRecurrenceError)
        etDescription.filters = arrayOf(ValidationUtils.getLengthFilter(ValidationUtils.MAX_LENGTH_NAME), ValidationUtils.getControlCharactersBlockFilter())
        val etAmount = dialogView.findViewById<EditText>(R.id.etExpenseAmount)
        etAmount.filters = currencyInputFilters()
        val btnSelectDate = dialogView.findViewById<Button>(R.id.btnSelectDate)
        val cbRecurring = dialogView.findViewById<CheckBox>(R.id.cbRecurring)
        val recurrenceOptions = dialogView.findViewById<LinearLayout>(R.id.recurrenceOptions)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)
        val radioMonthly = dialogView.findViewById<RadioButton>(R.id.radioMonthly)
        val radioEveryX = dialogView.findViewById<RadioButton>(R.id.radioEveryX)
        val etEveryXDays = dialogView.findViewById<EditText>(R.id.etEveryXDays)

        etEveryXDays.background = ContextCompat.getDrawable(this, R.drawable.edittext_background_everyx)

        // Pre-fill data
        etDescription.setText(ValidationUtils.sanitizeString(expense.description))
        tvDescriptionCounter.text = getString(R.string.character_counter, expense.description.length, ValidationUtils.MAX_LENGTH_NAME)
        etAmount.setText(expense.amount.toCurrencyEdit(resources))
        val cal = Calendar.getInstance().apply { timeInMillis = expense.dueDate }
        btnSelectDate.text = FULL.format(cal.time)
        cbRecurring.isChecked = expense.recurrenceType != RecurrenceType.NONE
        recurrenceOptions.visibility = if (cbRecurring.isChecked) View.VISIBLE else View.GONE

        when (expense.recurrenceType) {
            RecurrenceType.MONTHLY_SAME_DAY -> {
                radioMonthly.isChecked = true
                etEveryXDays.isEnabled = false
                etEveryXDays.setTextColor(ContextCompat.getColor(this, R.color.text_disabled))
                etEveryXDays.text.clear()
            }
            RecurrenceType.EVERY_X_DAYS -> {
                radioEveryX.isChecked = true
                etEveryXDays.setText(expense.recurrenceInterval?.toString() ?: "")
                etEveryXDays.isEnabled = true
                etEveryXDays.setTextColor(ContextCompat.getColor(this, R.color.text_on_container))
            }
            RecurrenceType.NONE -> {
                etEveryXDays.isEnabled = false
                etEveryXDays.setTextColor(ContextCompat.getColor(this, R.color.text_disabled))
            }
        }

        // Original values
        val originalDescription = expense.description
        val originalAmount = expense.amount
        val originalDate = expense.dueDate
        val originalRecurrenceType = expense.recurrenceType
        val originalInterval = expense.recurrenceInterval

        var selectedDate: Long? = expense.dueDate

        etEveryXDays.inputType = InputType.TYPE_CLASS_NUMBER
        etEveryXDays.filters = arrayOf(ValidationUtils.getLengthFilter(3))

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.edit_expense_title),
            view = dialogView,
            positiveButton = getString(R.string.save),
            negativeButton = getString(R.string.cancel)
        )

        fun getCurrentRecurrenceType(): RecurrenceType {
            return if (cbRecurring.isChecked) {
                when {
                    radioMonthly.isChecked -> RecurrenceType.MONTHLY_SAME_DAY
                    radioEveryX.isChecked -> RecurrenceType.EVERY_X_DAYS
                    else -> RecurrenceType.NONE
                }
            } else RecurrenceType.NONE
        }

        fun getCurrentInterval(): Int? {
            return if (getCurrentRecurrenceType() == RecurrenceType.EVERY_X_DAYS) {
                etEveryXDays.text.toString().toIntOrNull()
            } else null
        }

        fun validate() {
            val description = etDescription.text.toString()
            tvDescriptionCounter.text = getString(R.string.character_counter, description.length, ValidationUtils.MAX_LENGTH_NAME)
            val descriptionTrimmed = description.trim()
            val amountText = etAmount.text.toString().trim()
            val amount = amountText.toAmountOrNull(resources) ?: 0.0
            val dateValid = selectedDate != null
            val descriptionValid = descriptionTrimmed.isNotEmpty()
            val amountValid = amount >= 0 && ValidationUtils.isValidAmount(amount)

            if (amount >= 0 && !ValidationUtils.isValidAmount(amount)) {
                tvAmountError.text = getString(R.string.amount_exceeds_maximum)
                tvAmountError.visibility = View.VISIBLE
            } else {
                tvAmountError.visibility = View.INVISIBLE
            }

            var recurrenceValid = true
            if (cbRecurring.isChecked) {
                recurrenceValid = radioMonthly.isChecked || radioEveryX.isChecked
                if (radioEveryX.isChecked) {
                    val days = etEveryXDays.text.toString().toIntOrNull()
                    recurrenceValid = days != null && ValidationUtils.isValidRecurrenceDays(days)

                    if (days != null && !ValidationUtils.isValidRecurrenceDays(days)) {
                        tvRecurrenceError.text = getString(R.string.recurrence_exceeds_maximum)
                        tvRecurrenceError.visibility = View.VISIBLE
                    } else {
                        tvRecurrenceError.visibility = View.INVISIBLE
                    }
                } else {
                    tvRecurrenceError.visibility = View.INVISIBLE
                }
            } else {
                tvRecurrenceError.visibility = View.INVISIBLE
            }

            val currentRecurrence = getCurrentRecurrenceType()
            val currentInterval = getCurrentInterval()

            val changed = descriptionTrimmed != originalDescription ||
                    amount != originalAmount ||
                    selectedDate != originalDate ||
                    currentRecurrence != originalRecurrenceType ||
                    currentInterval != originalInterval

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                descriptionValid && amountValid && dateValid && recurrenceValid && changed
        }

        // Set up listeners
        cbRecurring.setOnCheckedChangeListener { _, isChecked ->
            recurrenceOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                radioMonthly.isChecked = false
                radioEveryX.isChecked = false
                etEveryXDays.text.clear()
            }
            validate()
        }

        etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validate() }
            override fun afterTextChanged(s: Editable?) {}
        })
        etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validate() }
            override fun afterTextChanged(s: Editable?) {}
        })
        etEveryXDays.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validate() }
            override fun afterTextChanged(s: Editable?) {}
        })

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioEveryX) {
                etEveryXDays.isEnabled = true
                etEveryXDays.setTextColor(ContextCompat.getColor(this, R.color.text_on_container))
            } else {
                etEveryXDays.isEnabled = false
                etEveryXDays.setTextColor(ContextCompat.getColor(this, R.color.text_disabled))
                etEveryXDays.text.clear()
            }
            validate()
        }

        btnSelectDate.setOnClickListener {
            hideKeyboard(btnSelectDate)
            val calendar = Calendar.getInstance().apply { timeInMillis = expense.dueDate }
            DatePickerDialog(
                this,
                R.style.ThemeOverlay_BudgetBrewer_DatePicker,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDate = calendar.timeInMillis
                    btnSelectDate.text = FULL.format(calendar.time)
                    validate()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        dialog.setOnShowListener {
            validate() // initially disabled
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val description = etDescription.text.toString().trim()
                val amount = etAmount.text.toString().toAmountOrNull(resources) ?: 0.0
                val date = selectedDate
                if (!ValidationUtils.isValidName(description)) return@setOnClickListener
                if (date != null && description.isNotEmpty() && amount >= 0) {
                    val recurrenceType = getCurrentRecurrenceType()
                    val interval = getCurrentInterval()
                    val updatedExpense = expense.copy(
                        description = description,
                        amount = amount,
                        dueDate = date,
                        recurrenceType = recurrenceType,
                        recurrenceInterval = interval
                    )
                    viewModel.updateExpense(updatedExpense)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    // ==================== ALLOCATION SECTION ====================

    private fun showAllocationDialog(type: AllocationType, existingAmount: Double = 0.0) {
        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_allocation, null, false)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAllocationAmount)
        etAmount.filters = currencyInputFilters()
        val tvError = dialogView.findViewById<TextView>(R.id.tvAllocationError)
        if (existingAmount > 0) etAmount.setText(existingAmount.toCurrencyEdit(resources))

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = type.displayName,
            view = dialogView,
            positiveButton = getString(R.string.save),
            negativeButton = getString(R.string.cancel)
        )

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.isEnabled = false

            val state = viewModel.uiState.value as? IncomeExpensesUiState.Success
            val totalIncome = state?.incomes?.sumOf { it.amount } ?: 0.0
            val totalExpenses = state?.expenses?.sumOf { it.amount } ?: 0.0
            val leftover = totalIncome - totalExpenses
            val allocation = viewModel.allocation.value
            val otherAllocated = when (type) {
                AllocationType.Savings -> allocation?.spendingAmount ?: 0.0
                else -> allocation?.savingsAmount ?: 0.0
            }

            etAmount.addTextChangedListener(createSimpleTextWatcher { s ->
                val input = s.toString().trim()
                val amount = input.toAmountOrNull(resources)

                val isValid = amount != null && amount > 0.0
                val withinLimit = amount != null && (amount + otherAllocated) <= leftover + Constants.EPSILON

                saveButton.isEnabled = isValid && withinLimit

                if (amount != null && amount > 0.0 && !withinLimit) {
                    val excess = amount + otherAllocated - leftover
                    tvError.text = getString(
                        R.string.allocation_exceeds_funds, excess.toCurrencyDisplay(resources)
                    )
                    tvError.visibility = View.VISIBLE
                } else {
                    tvError.visibility = View.INVISIBLE
                }
            })

            saveButton.setOnClickListener {
                val amount = etAmount.text.toString().trim().toAmountOrNull(resources) ?: 0.0
                if (type == AllocationType.Savings) {
                    viewModel.setSavingsAllocation(amount)
                } else {
                    viewModel.setSpendingAllocation(amount)
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showDeleteAllocationDialog(type: AllocationType) {
        showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = type.displayName,
            message = getString(R.string.allocation_delete_message),
            positiveButton = getString(R.string.delete),
            negativeButton = getString(R.string.cancel),
            onPositive = {
                if (type == AllocationType.Savings) {
                    viewModel.deleteSavingsAllocation()
                } else {
                    viewModel.deleteSpendingAllocation()
                }
            }
        ).show()
    }

    private fun updateAllocationRow(type: AllocationType, amount: Double, totalIncome: Double, leftover: Double) {
        val placeholder = if (type == AllocationType.Savings) binding.savingsPlaceholder else binding.spendingPlaceholder
        placeholder.removeAllViews()

        if (amount > 0) {
            // Show allocation item (edit/delete buttons)
            val itemView = layoutInflater.inflate(R.layout.item_allocation, placeholder, false)
            itemView.findViewById<TextView>(R.id.tvAllocationAmount).text = amount.toCurrencyDisplay(resources)
            val percent = if (totalIncome > 0) amount / totalIncome * 100 else 0.0
            itemView.findViewById<TextView>(R.id.tvAllocationPercent).text = percent.toPercentDisplay(resources)

            itemView.findViewById<MaterialButton>(R.id.btnEditAllocation).setOnClickListener {
                showAllocationDialog(type, amount)
            }
            itemView.findViewById<MaterialButton>(R.id.btnDeleteAllocation).setOnClickListener {
                showDeleteAllocationDialog(type)
            }

            itemView.contentDescription = buildString {
                append(type.displayName)
                append(" allocation, ")
                append(amount.toCurrencyDisplay(itemView.resources))
                append(", ")
                append(percent.toPercentDisplay(itemView.resources))
                append(", double tap to edit or delete")
            }

            placeholder.addView(itemView)
        } else {
            // Only show add button if there are funds available (leftover > 0)
            if (leftover > 0) {
                val addButton = layoutInflater.inflate(R.layout.button_add_allocation, placeholder, false) as MaterialButton
                addButton.setOnClickListener {
                    showAllocationDialog(type)
                }
                addButton.contentDescription = getString(R.string.add_allocation_content_desc, type.displayName)
                placeholder.addView(addButton)
            }
            // If leftover <= 0, no button is shown
        }
    }

    private fun updateLeftoverSection() {
        val state = viewModel.uiState.value
        if (state !is IncomeExpensesUiState.Success) return

        val totalIncome = state.incomes.sumOf { it.amount }
        val totalExpenses = state.expenses.sumOf { it.amount }
        val leftover = totalIncome - totalExpenses
        val leftoverPercent = if (totalIncome > 0) leftover / totalIncome * 100 else 0.0

        binding.tvLeftoverAmount.text = leftover.toCurrencyDisplay(resources)
        binding.tvLeftoverPercent.text = leftoverPercent.toPercentDisplay(resources)
        val allocation = viewModel.allocation.value
        // Pass leftover to updateAllocationRow
        updateAllocationRow(AllocationType.Savings, allocation?.savingsAmount ?: 0.0, totalIncome, leftover)
        updateAllocationRow(AllocationType.Spending, allocation?.spendingAmount ?: 0.0, totalIncome, leftover)

        val allocated = (allocation?.savingsAmount ?: 0.0) + (allocation?.spendingAmount ?: 0.0)

        val status = when {
            totalExpenses > totalIncome -> getString(R.string.allocation_status_expenses_exceed)
            leftover <= 0 -> getString(R.string.allocation_status_no_funds)
            allocated > leftover + Constants.EPSILON -> getString(R.string.allocation_status_overallocated)
            abs(allocated - leftover) < Constants.EPSILON -> getString(R.string.allocation_status_fully_allocated)
            else -> getString(R.string.allocation_status_available)
        }
        binding.tvAllocationStatus.text = status
        binding.tvAllocationStatus.contentDescription = status
    }

    // ==================== CATEGORIES UI ====================

    private fun updateCategoriesUI(categories: List<ExpenseCategory>, expenses: List<Expense> = emptyList()) {
        // Guard: if nothing changed and adapter exists, skip entirely
        if (categoriesList == categories && expensesList == expenses && binding.categoriesRecyclerView.adapter != null) return
        val categoriesChanged = categoriesList != categories
        categoriesList = categories
        expensesList = expenses

        if (categories.isEmpty()) {
            binding.tvEmptyCategories.visibility = View.VISIBLE
            binding.tvSwipeHint.visibility = View.GONE
            binding.categoriesRecyclerView.visibility = View.GONE
            return
        }

        binding.tvEmptyCategories.visibility = View.GONE
        binding.tvSwipeHint.visibility = if (categories.size > 1) View.VISIBLE else View.GONE

        if (categories.size > 1) {
            val leftArrow = ContextCompat.getDrawable(this, R.drawable.ic_chevron_left)
            val rightArrow = ContextCompat.getDrawable(this, R.drawable.ic_chevron_right)
            leftArrow?.setTint(ContextCompat.getColor(this, R.color.text_on_main))
            rightArrow?.setTint(ContextCompat.getColor(this, R.color.text_on_main))
            binding.tvSwipeHint.setCompoundDrawablesWithIntrinsicBounds(leftArrow, null, rightArrow, null)
            binding.tvSwipeHint.compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.spacing_small)
        } else {
            binding.tvSwipeHint.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
        }

        // Fast path: only expenses changed, reuse the adapter
        if (!categoriesChanged && binding.categoriesRecyclerView.adapter is CategoryAdapter) {
            val adapter = binding.categoriesRecyclerView.adapter as CategoryAdapter
            val state = viewModel.uiState.value as? IncomeExpensesUiState.Success
            if (state != null) {
                adapter.updateData(categories, state.expenses)
            }
            return
        }

        // Full rebuild path: categories changed (or first load)
        binding.categoriesRecyclerView.visibility = View.VISIBLE

        val state = viewModel.uiState.value as? IncomeExpensesUiState.Success ?: return
        val stateExpenses = state.expenses

        val isTablet = resources.getBoolean(R.bool.is_tablet)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val screenWidth = resources.displayMetrics.widthPixels
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.activity_horizontal_margin) * 2
        val gap = resources.getDimensionPixelSize(R.dimen.card_margin)

        val contentWidth: Int
        val halfMargin: Int

        if (isTablet) {
            if (isLandscape) {
                contentWidth = resources.getDimensionPixelSize(R.dimen.category_card_width_land)
                halfMargin = gap / 2
            } else {
                val desiredCardWidthFactor = 0.8f
                val availableWidth = screenWidth - horizontalPadding - gap
                contentWidth = (availableWidth * desiredCardWidthFactor).toInt()
                halfMargin = gap / 2
            }
            binding.categoriesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.categoriesRecyclerView.isNestedScrollingEnabled = false
        } else {
            if (isLandscape) {
                val availableWidth = screenWidth - horizontalPadding - gap
                contentWidth = (availableWidth * 0.40f).toInt().coerceAtMost(
                    resources.getDimensionPixelSize(R.dimen.category_card_width_land)
                )
                halfMargin = gap / 2
            } else {
                val extraReduction = resources.getDimensionPixelSize(R.dimen.card_width_reduction)
                contentWidth = screenWidth - horizontalPadding - extraReduction - gap
                halfMargin = gap / 2
            }
            binding.categoriesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.categoriesRecyclerView.isNestedScrollingEnabled = false
        }

        // Focus handling for add/delete
        if (categories.size > previousCategoriesSize && previousCategoriesSize > 0) {
            currentCategoryIndex = categories.size - 1
        } else if (categories.size < previousCategoriesSize) {
            if (currentCategoryIndex >= categories.size) {
                currentCategoryIndex = (categories.size - 1).coerceAtLeast(0)
            }
        }
        previousCategoriesSize = categories.size

        // Create adapter (always infinite scrolling, so isGrid = false)
        val adapter = CategoryAdapter(
            categories = categories,
            allExpenses = stateExpenses,
            contentWidth = contentWidth,
            halfMargin = halfMargin,
            isGrid = false,
            onEditCategory = { category -> showEditCategoryDialog(category) },
            onDeleteCategory = { category -> showDeleteCategoryDialog(category) },
            onAddExpense = { category ->
                val innerState = viewModel.uiState.value
                if (innerState is IncomeExpensesUiState.Success) {
                    val expensesForCategory = innerState.expenses.filter { it.categoryId == category.id }
                    if (expensesForCategory.size >= Constants.MAX_EXPENSES_PER_CATEGORY) {
                        showSnackbar(getString(R.string.max_expenses_per_category_reached, Constants.MAX_EXPENSES_PER_CATEGORY))
                    } else {
                        showAddExpenseDialog(category)
                    }
                }
            },
            onEditExpense = { expense -> showEditExpenseDialog(expense) },
            onDeleteExpense = { expense -> viewModel.deleteExpense(expense) }
        )
        binding.categoriesRecyclerView.adapter = adapter

        // Clear any existing fling listener and attach PagerSnapHelper
        binding.categoriesRecyclerView.onFlingListener = null
        val pagerSnapHelper = PagerSnapHelper()
        pagerSnapHelper.attachToRecyclerView(binding.categoriesRecyclerView)

        if (categories.isNotEmpty()) {
            val startPosition = (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % categories.size) +
                    (currentCategoryIndex % categories.size)

            binding.categoriesRecyclerView.post {
                val layoutManager = binding.categoriesRecyclerView.layoutManager as LinearLayoutManager
                val recyclerViewWidth = binding.categoriesRecyclerView.width
                val contentAreaWidth = recyclerViewWidth - binding.categoriesRecyclerView.paddingLeft - binding.categoriesRecyclerView.paddingRight
                val itemTotalWidth = contentWidth + (halfMargin * 2)
                val targetOffset = (contentAreaWidth - itemTotalWidth) / 2
                layoutManager.scrollToPositionWithOffset(startPosition, targetOffset)
            }
        }
    }
}

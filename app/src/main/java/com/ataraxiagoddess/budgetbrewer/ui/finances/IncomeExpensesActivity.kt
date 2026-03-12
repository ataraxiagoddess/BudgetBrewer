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
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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
import com.ataraxiagoddess.budgetbrewer.ui.settings.SettingsActivity
import com.ataraxiagoddess.budgetbrewer.ui.spending.SpendingActivity
import com.ataraxiagoddess.budgetbrewer.util.Constants
import com.ataraxiagoddess.budgetbrewer.util.DecimalDigitsInputFilter
import com.ataraxiagoddess.budgetbrewer.util.FULL
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyDisplay
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyEdit
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyFormat
import com.ataraxiagoddess.budgetbrewer.util.toPercentDisplay
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class IncomeExpensesActivity : BaseActivity(), MonthChangeListener {

    override val currentNavDestination: NavDestination
        get() = NavDestination.FINANCES
    private lateinit var binding: ActivityIncomeExpensesBinding
    private lateinit var repository: BudgetRepository
    private val viewModel: IncomeExpensesViewModel by viewModels {
        IncomeExpensesViewModelFactory(repository, this)
    }
    private val freqKey = "selected_frequency"
    private var previousFrequency: Frequency? = null
    private var isProgrammaticChange = false
    private var snapHelper: PagerSnapHelper? = null
    private var currentCategoryIndex: Int = 0
    private var categoriesList: List<ExpenseCategory> = emptyList()
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
        val btnEdit: ImageButton = rowView.findViewById(R.id.btnEditIncome)
        val btnDelete: ImageButton = rowView.findViewById(R.id.btnDeleteIncome)
        var weekNumber: Int = 0
        var frequency: Frequency = Frequency.MONTHLY
    }

    class TipEntryHolder(itemView: View) {
        val tvSource: TextView = itemView.findViewById(R.id.tvTipSource)
        val tvAmount: TextView = itemView.findViewById(R.id.tvTipAmount)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditTip)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteTip)

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

    private fun setupAmountSourceDialog(
        dialog: AlertDialog,
        etSource: EditText,
        etAmount: EditText,
        onValidated: (source: String, amount: Double) -> Unit
    ) {

        dialog.setOnShowListener {
            val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            addButton.isEnabled = false

            val textWatcher = createSimpleTextWatcher {
                val sourceValid = !etSource.text.isNullOrBlank()
                val amountValid = etAmount.text.toString().toDoubleOrNull() != null
                addButton.isEnabled = sourceValid && amountValid
            }
            etSource.addTextChangedListener(textWatcher)
            etAmount.addTextChangedListener(textWatcher)

            addButton.setOnClickListener {
                val source = etSource.text.toString().trim()
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                onValidated(source, amount)
                dialog.dismiss()
            }
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun getCurrentFrequency(): Frequency =
        Frequency.valueOf(binding.spinnerFrequency.selectedItem.toString())

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        window.attributes.windowAnimations = 0
        super.onCreate(savedInstanceState)

        binding = ActivityIncomeExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)
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

        addMonthChangeListener(this)

        setupFrequencySpinner()
        setupAddCategoryButton()
        setupTipsCheckbox()
        observeTips()

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
                        val currentFrequency = getCurrentFrequency()
                        rebuildIncomeRows(currentFrequency, state.incomes)
                        updateCategoriesUI(state.categories)
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

    private fun setupFrequencySpinner() {
        val frequencies = Frequency.entries.map { it.name }.toTypedArray()

        val adapter = object : ArrayAdapter<String>(
            this,
            R.layout.spinner_closed,
            android.R.id.text1,
            frequencies
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

        val savedFreq = prefs.getString(freqKey, Frequency.MONTHLY.name)
        val position = frequencies.indexOf(savedFreq).takeIf { it >= 0 } ?: 0
        binding.spinnerFrequency.setSelection(position)

        binding.spinnerFrequency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (isProgrammaticChange) return

                val newFrequency = Frequency.valueOf(parent?.getItemAtPosition(pos).toString())
                val previous = previousFrequency

                if (previous == null) {
                    previousFrequency = newFrequency
                    prefs.edit { putString(freqKey, newFrequency.name) }
                    val currentIncomes = (viewModel.uiState.value as? IncomeExpensesUiState.Success)?.incomes ?: emptyList()
                    rebuildIncomeRows(newFrequency, currentIncomes)
                    return
                }

                if (newFrequency == previous) {
                    prefs.edit { putString(freqKey, newFrequency.name) }
                    val currentIncomes = (viewModel.uiState.value as? IncomeExpensesUiState.Success)?.incomes ?: emptyList()
                    rebuildIncomeRows(newFrequency, currentIncomes)
                    return
                }

                val incomes = (viewModel.uiState.value as? IncomeExpensesUiState.Success)?.incomes ?: emptyList()
                if (incomes.isEmpty()) {
                    previousFrequency = newFrequency
                    prefs.edit { putString(freqKey, newFrequency.name) }
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
                        previousFrequency = newFrequency
                        prefs.edit { putString(freqKey, newFrequency.name) }
                        dialog.dismiss()
                    }

                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        isProgrammaticChange = true
                        binding.spinnerFrequency.setSelection(frequencies.indexOf(previous.name))
                        isProgrammaticChange = false
                        dialog.dismiss()
                    }
                }

                dialog.show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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
            } else {
                holder.tvSource.visibility = View.GONE
                holder.tvAmount.visibility = View.GONE
                holder.btnAdd.visibility = View.VISIBLE
                holder.btnEdit.visibility = View.GONE
                holder.btnDelete.visibility = View.GONE

                holder.btnAdd.setOnClickListener { showAddIncomeDialog(row.weekNumber) }
            }

            rowView.tag = holder
            binding.incomeFieldsContainer.addView(rowView)
        }
    }

    // ==================== TIPS SECTION ====================

    private fun setupTipsCheckbox() {
        val wasChecked = prefs.getBoolean("tips_enabled", false)
        binding.checkBoxTips.isChecked = wasChecked
        if (wasChecked) {
            binding.tipsSectionContainer.visibility = View.VISIBLE
            rebuildTipsGrid(viewModel.tipsList.value)
        } else {
            binding.tipsSectionContainer.visibility = View.GONE
        }

        binding.checkBoxTips.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.tipsSectionContainer.visibility = View.VISIBLE
                rebuildTipsGrid(viewModel.tipsList.value)
                prefs.edit { putBoolean("tips_enabled", true) }
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
                        prefs.edit { putBoolean("tips_enabled", false) }
                        dialog.dismiss()
                    }

                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
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
        val etAmount = dialogView.findViewById<EditText>(R.id.etIncomeAmount)
        etAmount.filters = arrayOf(DecimalDigitsInputFilter())

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.add_income_dialog_title),
            view = dialogView,
            positiveButton = getString(R.string.add),
            negativeButton = getString(R.string.cancel)
        )

        setupAmountSourceDialog(dialog, etSource, etAmount) { source, amount ->
            val frequency = getCurrentFrequency()
            viewModel.addIncome(source, amount, frequency, weekNumber = weekNumber)
        }

        dialog.show()
    }

    private fun showEditIncomeDialog(income: Income) {
        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_income, null, false)
        val etSource = dialogView.findViewById<EditText>(R.id.etIncomeSource)
        val etAmount = dialogView.findViewById<EditText>(R.id.etIncomeAmount)
        etAmount.filters = arrayOf(DecimalDigitsInputFilter())
        etSource.setText(income.sourceName)
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
            val amount = amountText.toDoubleOrNull() ?: 0.0
            val changed = source != originalSource || amount != originalAmount
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = changed
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validate() }
            override fun afterTextChanged(s: Editable?) {}
        }
        etSource.addTextChangedListener(textWatcher)
        etAmount.addTextChangedListener(textWatcher)

        dialog.setOnShowListener {
            validate() // initially disabled
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val source = etSource.text.toString().trim()
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                if (source.isNotEmpty() && amount > 0) {
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
        val etAmount = dialogView.findViewById<EditText>(R.id.etTipAmount)
        etAmount.filters = arrayOf(DecimalDigitsInputFilter())

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.add_tip_dialog_title),
            view = dialogView,
            positiveButton = getString(R.string.add),
            negativeButton = getString(R.string.cancel)
        )

        setupAmountSourceDialog(dialog, etSource, etAmount) { source, amount ->
            val maxOrder = viewModel.tipsList.value.maxOfOrNull { it.tipsOrder ?: 0 } ?: 0
            viewModel.addTip(source, amount, maxOrder + 1)
        }

        dialog.show()
    }

    private fun showEditTipDialog(tip: Income) {
        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_tip, null, false)
        val etSource = dialogView.findViewById<EditText>(R.id.etTipSource)
        val etAmount = dialogView.findViewById<EditText>(R.id.etTipAmount)
        etAmount.filters = arrayOf(DecimalDigitsInputFilter())
        etSource.setText(tip.sourceName)
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
            val amount = amountText.toDoubleOrNull() ?: 0.0
            val changed = source != originalSource || amount != originalAmount
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = changed
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validate() }
            override fun afterTextChanged(s: Editable?) {}
        }
        etSource.addTextChangedListener(textWatcher)
        etAmount.addTextChangedListener(textWatcher)

        dialog.setOnShowListener {
            validate()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val source = etSource.text.toString().trim()
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                if (source.isNotEmpty() && amount > 0) {
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
                if (name.isNotEmpty()) {
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
        etName.setText(category.name)

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
                if (newName.isNotEmpty()) {
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
        val etAmount = dialogView.findViewById<EditText>(R.id.etExpenseAmount)
        etAmount.filters = arrayOf(DecimalDigitsInputFilter())
        val btnSelectDate = dialogView.findViewById<Button>(R.id.btnSelectDate)
        val cbRecurring = dialogView.findViewById<CheckBox>(R.id.cbRecurring)
        val recurrenceOptions = dialogView.findViewById<LinearLayout>(R.id.recurrenceOptions)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)
        val radioMonthly = dialogView.findViewById<RadioButton>(R.id.radioMonthly)
        val radioEveryX = dialogView.findViewById<RadioButton>(R.id.radioEveryX)
        val etEveryXDays = dialogView.findViewById<EditText>(R.id.etEveryXDays)

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
        etEveryXDays.filters = arrayOf(InputFilter.LengthFilter(5))

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
            val descriptionValid = !etDescription.text.isNullOrBlank()
            val amountValid = etAmount.text.toString().toDoubleOrNull() != null
            val dateValid = selectedDate != null

            var recurrenceValid = true
            if (cbRecurring.isChecked) {
                recurrenceValid = radioMonthly.isChecked || radioEveryX.isChecked
                if (radioEveryX.isChecked) {
                    val daysText = etEveryXDays.text.toString()
                    val days = daysText.toIntOrNull()
                    recurrenceValid = days != null && days > 0
                }
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
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                val date = selectedDate
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
        val etAmount = dialogView.findViewById<EditText>(R.id.etExpenseAmount)
        etAmount.filters = arrayOf(DecimalDigitsInputFilter())
        val btnSelectDate = dialogView.findViewById<Button>(R.id.btnSelectDate)
        val cbRecurring = dialogView.findViewById<CheckBox>(R.id.cbRecurring)
        val recurrenceOptions = dialogView.findViewById<LinearLayout>(R.id.recurrenceOptions)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)
        val radioMonthly = dialogView.findViewById<RadioButton>(R.id.radioMonthly)
        val radioEveryX = dialogView.findViewById<RadioButton>(R.id.radioEveryX)
        val etEveryXDays = dialogView.findViewById<EditText>(R.id.etEveryXDays)

        etEveryXDays.background = ContextCompat.getDrawable(this, R.drawable.edittext_background_everyx)

        // Pre-fill data
        etDescription.setText(expense.description)
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
        etEveryXDays.filters = arrayOf(InputFilter.LengthFilter(5))

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
            val description = etDescription.text.toString().trim()
            val amountText = etAmount.text.toString().trim()
            val amount = amountText.toDoubleOrNull() ?: 0.0
            val dateValid = selectedDate != null
            val descriptionValid = description.isNotEmpty()
            val amountValid = amount > 0

            var recurrenceValid = true
            if (cbRecurring.isChecked) {
                recurrenceValid = radioMonthly.isChecked || radioEveryX.isChecked
                if (radioEveryX.isChecked) {
                    val days = etEveryXDays.text.toString().toIntOrNull()
                    recurrenceValid = days != null && days > 0
                }
            }

            val currentRecurrence = getCurrentRecurrenceType()
            val currentInterval = getCurrentInterval()

            val changed = description != originalDescription ||
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
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                val date = selectedDate
                if (date != null && description.isNotEmpty() && amount > 0) {
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
        etAmount.filters = arrayOf(DecimalDigitsInputFilter())
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
                val amount = input.toDoubleOrNull()

                val isValid = amount != null && amount > 0.0
                val withinLimit = amount != null && (amount + otherAllocated) <= leftover + Constants.EPSILON

                saveButton.isEnabled = isValid && withinLimit

                if (amount != null && amount > 0.0 && !withinLimit) {
                    val excess = amount + otherAllocated - leftover
                    tvError.text = getString(
                        R.string.allocation_exceeds_funds, String.format(
                            Locale.US, "%.2f", excess))
                    tvError.visibility = View.VISIBLE
                } else {
                    tvError.visibility = View.GONE
                }
            })

            saveButton.setOnClickListener {
                val amount = etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
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

            itemView.findViewById<ImageButton>(R.id.btnEditAllocation).setOnClickListener {
                showAllocationDialog(type, amount)
            }
            itemView.findViewById<ImageButton>(R.id.btnDeleteAllocation).setOnClickListener {
                showDeleteAllocationDialog(type)
            }

            placeholder.addView(itemView)
        } else {
            // Only show add button if there are funds available (leftover > 0)
            if (leftover > 0) {
                val addButton = layoutInflater.inflate(R.layout.button_add_allocation, placeholder, false) as MaterialButton
                addButton.setOnClickListener {
                    showAllocationDialog(type)
                }
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
    }

    // ==================== CATEGORIES UI ====================

    private fun updateCategoriesUI(categories: List<ExpenseCategory>) {
        categoriesList = categories

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

        binding.categoriesRecyclerView.visibility = View.INVISIBLE

        val state = viewModel.uiState.value as? IncomeExpensesUiState.Success ?: return
        val expenses = state.expenses

        val isTablet = resources.getBoolean(R.bool.is_tablet)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val screenWidth = resources.displayMetrics.widthPixels
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.activity_horizontal_margin) * 2
        val gap = resources.getDimensionPixelSize(R.dimen.card_margin)

        val contentWidth: Int
        val halfMargin: Int

        if (isTablet) {
            if (isLandscape) {
                // Tablet landscape: use fixed width from dimension
                contentWidth = resources.getDimensionPixelSize(R.dimen.category_card_width_land)
                halfMargin = gap / 2
            } else {
                // Tablet portrait: use percentage of screen width
                val desiredCardWidthFactor = 0.8f
                val availableWidth = screenWidth - horizontalPadding - gap
                contentWidth = (availableWidth * desiredCardWidthFactor).toInt()
                halfMargin = gap / 2
            }
            // Always use horizontal LinearLayoutManager for tablets (no grid)
            binding.categoriesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.categoriesRecyclerView.isNestedScrollingEnabled = false
        } else {
            // Phone
            if (isLandscape) {
                // Phone landscape: use fixed width from dimension
                contentWidth = resources.getDimensionPixelSize(R.dimen.category_card_width_land)
                halfMargin = gap / 2
            } else {
                // Phone portrait: original calculation
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
            allExpenses = expenses,
            contentWidth = contentWidth,
            halfMargin = halfMargin,
            isGrid = false,
            onEditCategory = { category -> showEditCategoryDialog(category) },
            onDeleteCategory = { category -> showDeleteCategoryDialog(category) },
            onAddExpense = { category ->
                val state = viewModel.uiState.value
                if (state is IncomeExpensesUiState.Success) {
                    val expensesForCategory = state.expenses.filter { it.categoryId == category.id }
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

                binding.categoriesRecyclerView.visibility = View.VISIBLE
            }
        } else {
            binding.categoriesRecyclerView.visibility = View.VISIBLE
        }
    }
}
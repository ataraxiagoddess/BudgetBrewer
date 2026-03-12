package com.ataraxiagoddess.budgetbrewer.ui.spending

import android.app.ActivityOptions
import android.app.DatePickerDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.ataraxiagoddess.budgetbrewer.MainActivity
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.SpendingEntry
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import com.ataraxiagoddess.budgetbrewer.databinding.ActivitySpendingBinding
import com.ataraxiagoddess.budgetbrewer.databinding.DialogAddTransactionBinding
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseActivity
import com.ataraxiagoddess.budgetbrewer.ui.base.MonthChangeListener
import com.ataraxiagoddess.budgetbrewer.ui.base.showBudgetBrewerDialog
import com.ataraxiagoddess.budgetbrewer.ui.calendar.MonthlyCalendarActivity
import com.ataraxiagoddess.budgetbrewer.ui.expenses.MonthlyExpenseListActivity
import com.ataraxiagoddess.budgetbrewer.ui.finances.IncomeExpensesActivity
import com.ataraxiagoddess.budgetbrewer.ui.finances.UiEvent
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import com.ataraxiagoddess.budgetbrewer.ui.navigation.NavDestination
import com.ataraxiagoddess.budgetbrewer.ui.settings.SettingsActivity
import com.ataraxiagoddess.budgetbrewer.util.Constants.DateFormats.FULL
import com.ataraxiagoddess.budgetbrewer.util.DecimalDigitsInputFilter
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyDisplay
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyEdit
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SpendingActivity : BaseActivity(), MonthChangeListener {

    override val currentNavDestination: NavDestination
        get() = NavDestination.SPENDING

    private lateinit var binding: ActivitySpendingBinding
    private lateinit var repository: BudgetRepository
    private lateinit var adapter: SpendingEntryAdapter

    private val viewModel: SpendingViewModel by viewModels {
        SpendingViewModelFactory(repository, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpendingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.getDatabase(this)
        repository = BudgetRepository(db)

        addMonthChangeListener(this)

        setupAddButton()

        adapter = SpendingEntryAdapter(
            onEditClick = { entry ->
                val state = viewModel.uiState.value
                if (state is SpendingViewModel.SpendingUiState.Success) {
                    showEditTransactionDialog(entry, state.data.remaining)
                }
            },
            onDeleteClick = { entry ->
                showDeleteConfirmationDialog(entry)
            }
        )

        setupRecyclerView()

        observeData()
        observeEvents()
    }

    private fun setupRecyclerView() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            // Landscape: 2 columns
            val gridLayoutManager = GridLayoutManager(this, 2)
            binding.recyclerView.layoutManager = gridLayoutManager
        } else {
            // Portrait: single column
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
        }

        binding.recyclerView.adapter = adapter
        binding.recyclerView.isNestedScrollingEnabled = false
    }

    private fun setupAddButton() {
        binding.btnAddTransaction.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is SpendingViewModel.SpendingUiState.Success) {
                showAddTransactionDialog(state.data.remaining)
            }
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is SpendingViewModel.SpendingUiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is SpendingViewModel.SpendingUiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        updateTransactionList(state.data.entries)
                        updateBubbles(state.data)
                    }
                    is SpendingViewModel.SpendingUiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewModel.event.collect { event ->
                when (event) {
                    UiEvent.SpendingAdded -> showSnackbar(getString(R.string.transaction_saved))
                    UiEvent.SpendingUpdated -> showSnackbar(getString(R.string.transaction_updated))
                    UiEvent.SpendingDeleted -> showSnackbar(getString(R.string.transaction_deleted))
                    else -> {}
                }
            }
        }
    }

    private fun updateTransactionList(entries: List<SpendingEntry>) {
        adapter.submitList(entries)
        if (entries.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
        }
    }

    private fun updateBubbles(data: SpendingViewModel.SpendingUiData) {
        val spendingAmount = data.allocation?.spendingAmount ?: 0.0
        binding.tvSpendingAllocation.text = getString(R.string.spending_bubble, spendingAmount.toCurrencyDisplay(resources))
        binding.tvRemaining.text = getString(R.string.remaining_bubble, data.remaining.toCurrencyDisplay(resources))
    }

    private fun showAddTransactionDialog(remaining: Double) {
        val dialogBinding = DialogAddTransactionBinding.inflate(layoutInflater)
        dialogBinding.etAmount.filters = arrayOf(DecimalDigitsInputFilter())

        var selectedDate: Long? = null

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.add_transaction_title),
            view = dialogBinding.root,
            positiveButton = getString(R.string.add),
            negativeButton = getString(R.string.cancel)
        )

        dialogBinding.btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                R.style.ThemeOverlay_BudgetBrewer_DatePicker,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDate = calendar.timeInMillis
                    dialogBinding.btnSelectDate.text = FULL.format(calendar.time)
                    validateAddDialog(dialog, dialogBinding, selectedDate, remaining)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateAddDialog(dialog, dialogBinding, selectedDate, remaining)
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        dialogBinding.etSource.addTextChangedListener(textWatcher)
        dialogBinding.etAmount.addTextChangedListener(textWatcher)

        dialog.setOnShowListener {
            validateAddDialog(dialog, dialogBinding, selectedDate, remaining)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val source = dialogBinding.etSource.text.toString().trim()
                val amount = dialogBinding.etAmount.text.toString().toDoubleOrNull() ?: 0.0
                val date = selectedDate
                if (date != null && source.isNotEmpty() && amount > 0 && amount <= remaining) {
                    viewModel.addEntry(date, source, amount)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun validateAddDialog(dialog: AlertDialog, binding: DialogAddTransactionBinding, date: Long?, remaining: Double) {
        val sourceValid = !binding.etSource.text.isNullOrBlank()
        val amountText = binding.etAmount.text.toString()
        val amount = amountText.toDoubleOrNull()
        val amountValid = amount != null && amount > 0
        val dateValid = date != null
        val withinLimit = amount != null && amount <= remaining

        binding.tvWarning.visibility = if (amount != null && amount > 0 && !withinLimit) View.VISIBLE else View.GONE

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = sourceValid && amountValid && dateValid && withinLimit
    }

    private fun showEditTransactionDialog(entry: SpendingEntry, remaining: Double) {
        val dialogBinding = DialogAddTransactionBinding.inflate(layoutInflater)
        dialogBinding.etAmount.filters = arrayOf(DecimalDigitsInputFilter())

        // Pre-fill
        dialogBinding.etSource.setText(entry.source)
        dialogBinding.etAmount.setText(entry.amount.toCurrencyEdit(resources))
        val calendar = Calendar.getInstance().apply { timeInMillis = entry.date }
        dialogBinding.btnSelectDate.text = FULL.format(calendar.time)

        var selectedDate: Long? = entry.date
        val originalSource = entry.source
        val originalAmount = entry.amount
        val originalDate = entry.date

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.edit_transaction_title),
            view = dialogBinding.root,
            positiveButton = getString(R.string.save),
            negativeButton = getString(R.string.cancel)
        )

        dialogBinding.btnSelectDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = entry.date }
            DatePickerDialog(
                this,
                R.style.ThemeOverlay_BudgetBrewer_DatePicker,
                { _, year, month, dayOfMonth ->
                    cal.set(year, month, dayOfMonth)
                    selectedDate = cal.timeInMillis
                    dialogBinding.btnSelectDate.text = FULL.format(cal.time)
                    validateEditDialog(dialog, dialogBinding, selectedDate, originalSource, originalAmount, originalDate, remaining)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateEditDialog(dialog, dialogBinding, selectedDate, originalSource, originalAmount, originalDate, remaining)
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        dialogBinding.etSource.addTextChangedListener(textWatcher)
        dialogBinding.etAmount.addTextChangedListener(textWatcher)

        dialog.setOnShowListener {
            validateEditDialog(dialog, dialogBinding, selectedDate, originalSource, originalAmount, originalDate, remaining)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newSource = dialogBinding.etSource.text.toString().trim()
                val newAmount = dialogBinding.etAmount.text.toString().toDoubleOrNull() ?: 0.0
                val newDate = selectedDate
                if (newDate != null && newSource.isNotEmpty() && newAmount > 0 && newAmount <= remaining) {
                    val updatedEntry = entry.copy(
                        source = newSource,
                        amount = newAmount,
                        date = newDate
                    )
                    viewModel.updateEntry(updatedEntry)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun validateEditDialog(
        dialog: AlertDialog,
        binding: DialogAddTransactionBinding,
        date: Long?,
        originalSource: String,
        originalAmount: Double,
        originalDate: Long,
        remaining: Double
    ) {
        val source = binding.etSource.text.toString().trim()
        val amountText = binding.etAmount.text.toString().trim()
        val amount = amountText.toDoubleOrNull() ?: 0.0

        val sourceValid = source.isNotEmpty()
        val amountValid = amount > 0
        val dateValid = date != null
        val withinLimit = amount <= remaining

        val changed = source != originalSource || amount != originalAmount || date != originalDate

        binding.tvWarning.visibility = if (amount > 0 && !withinLimit) View.VISIBLE else View.GONE

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = sourceValid && amountValid && dateValid && withinLimit && changed
    }

    private fun showDeleteConfirmationDialog(entry: SpendingEntry) {
        showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.delete_transaction_title),
            message = getString(R.string.delete_transaction_message),
            positiveButton = getString(R.string.delete),
            negativeButton = getString(R.string.cancel),
            onPositive = {
                viewModel.deleteEntry(entry)
            }
        ).show()
    }

    override fun onMonthChanged(month: Month) {
        Timber.d("SpendingActivity month changed: ${month.getDisplayName(this)}")
        viewModel.updateMonth(month)
    }

    override fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToFinances() {
        val intent = Intent(this, IncomeExpensesActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToExpenses() {
        val intent = Intent(this, MonthlyExpenseListActivity::class.java)
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

    override fun onDestroy() {
        removeMonthChangeListener(this)
        super.onDestroy()
    }
}
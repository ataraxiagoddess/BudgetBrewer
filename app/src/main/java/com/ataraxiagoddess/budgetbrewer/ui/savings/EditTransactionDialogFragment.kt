/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransaction
import com.ataraxiagoddess.budgetbrewer.databinding.DialogDistributeBinding
import com.ataraxiagoddess.budgetbrewer.util.CurrencyPrefs
import com.ataraxiagoddess.budgetbrewer.util.DecimalDigitsInputFilter
import java.util.Locale
import kotlin.math.abs

class EditTransactionDialogFragment(
    private val transaction: SavingsTransaction,
    private val maxAllowed: Double,
    private val onSave: (Double) -> Unit,
) : DialogFragment() {
    init {
        setStyle(STYLE_NORMAL, R.style.AlertDialogTheme_BudgetBrewer)
    }

    private var _binding: DialogDistributeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogDistributeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.etAmount.requestFocus()

        binding.tvBucketName.text = getString(R.string.edit_transaction_title)
        binding.tvAvailablePool.text = getString(R.string.max_deduction_label, maxAllowed)

        val originalAmount = abs(transaction.amount)
        val locales = resources.configuration.locales
        val locale = if (locales.isEmpty) Locale.getDefault() else locales[0]
        binding.etAmount.setText(String.format(Locale.US, "%.2f", originalAmount))
        binding.etAmount.filters =
            arrayOf(DecimalDigitsInputFilter(CurrencyPrefs.currentFractionDigits, CurrencyPrefs.decimalSeparators(locale)))

        val saveButton = binding.buttonSave
        saveButton.isEnabled = false

        binding.etAmount.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    val newAmount = s.toString().toDoubleOrNull()
                    val isValid = newAmount != null && newAmount > 0.0 && newAmount != originalAmount && newAmount <= maxAllowed
                    val showError = newAmount != null && newAmount > 0.0 && newAmount > maxAllowed

                    binding.tvError.visibility = if (showError) View.VISIBLE else View.INVISIBLE
                    binding.tvError.text = if (showError) getString(R.string.amount_exceeds_maximum_allowed) else ""
                    saveButton.isEnabled = isValid
                }

                override fun afterTextChanged(s: Editable?) {}
            },
        )

        binding.buttonCancel.setOnClickListener { dismiss() }
        binding.buttonSave.setOnClickListener {
            val newAmount =
                binding.etAmount.text
                    .toString()
                    .toDoubleOrNull()
            if (newAmount != null && newAmount > 0.0 && newAmount <= maxAllowed) {
                // Preserve the original sign of the transaction
                val signedAmount = if (transaction.amount < 0) -newAmount else newAmount
                onSave(signedAmount)
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

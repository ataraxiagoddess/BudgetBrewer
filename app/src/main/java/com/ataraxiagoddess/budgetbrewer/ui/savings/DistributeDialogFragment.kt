package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.databinding.DialogDistributeBinding

class DistributeDialogFragment(
    private val bucket: SavingsBucket,
    private val availablePool: Double,
    private val isDeduction: Boolean = false,
    private val onDistribute: (Double) -> Unit,
    private val onShowSnackbar: (String) -> Unit
) : DialogFragment() {

    init {
        setStyle(STYLE_NORMAL, R.style.AlertDialogTheme_BudgetBrewer)
    }

    private var _binding: DialogDistributeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDistributeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvBucketName.text = bucket.name

        val maxAllowed = if (isDeduction) bucket.current_amount else availablePool

        if (isDeduction) {
            binding.tvAvailablePool.text = getString(R.string.max_deduction_label, maxAllowed)
        } else {
            binding.tvAvailablePool.text = getString(R.string.available_pool_amount, availablePool)
        }

        val saveButton = binding.buttonSave
        saveButton.isEnabled = false

        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val amount = s.toString().toDoubleOrNull() ?: 0.0
                val errorMessage = when {
                    amount <= 0.0 -> "Amount must be greater than zero"
                    amount > maxAllowed -> "Exceeds maximum allowed"
                    else -> null
                }
                binding.tvError.visibility = if (errorMessage != null) View.VISIBLE else View.GONE
                binding.tvError.text = errorMessage ?: ""
                saveButton.isEnabled = amount > 0.0 && amount <= maxAllowed

                // Apply digit filter to prevent negative numbers and limit decimals
                binding.etAmount.filters = arrayOf(com.ataraxiagoddess.budgetbrewer.util.DecimalDigitsInputFilter())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.buttonCancel.setOnClickListener { dismiss() }
        binding.buttonSave.setOnClickListener {
            val amount = binding.etAmount.text.toString().toDoubleOrNull()
            if (amount != null && amount > 0.0 && amount <= maxAllowed) {
                onDistribute(amount)
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
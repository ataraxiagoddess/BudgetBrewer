package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.databinding.DialogDistributeBinding

class EditTransactionDialogFragment(
    private val currentAmount: Double,
    private val onSave: (Double) -> Unit,
    private val onShowSnackbar: (String) -> Unit
) : DialogFragment() {

    init { setStyle(STYLE_NORMAL, R.style.AlertDialogTheme_BudgetBrewer) }

    private var _binding: DialogDistributeBinding? = null
    private val binding get() = _binding!!

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

        binding.tvBucketName.text = getString(R.string.edit_transaction_title)
        binding.tvAvailablePool.text = ""
        binding.etAmount.setText(currentAmount.toString())
        binding.etAmount.filters = arrayOf(com.ataraxiagoddess.budgetbrewer.util.DecimalDigitsInputFilter())

        binding.buttonCancel.setOnClickListener { dismiss() }
        binding.buttonSave.setOnClickListener {
            val newAmount = binding.etAmount.text.toString().toDoubleOrNull()
            if (newAmount != null) {
                onSave(newAmount)
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransaction
import com.ataraxiagoddess.budgetbrewer.databinding.DialogBucketHistoryBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BucketHistoryDialogFragment(
    private val bucketName: String,
    private val bucketId: String,
    private val repository: BudgetRepository,
    private val isArchived: Boolean = false,
    // Callbacks for editing/deleting transactions (null when read‑only)
    private val onEditTransaction: ((SavingsTransaction) -> Unit)? = null,
    private val onDeleteTransaction: ((SavingsTransaction) -> Unit)? = null
) : DialogFragment() {

    init {
        setStyle(STYLE_NORMAL, R.style.AlertDialogTheme_BudgetBrewer)
    }

    private var _binding: DialogBucketHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogBucketHistoryBinding.inflate(inflater, container, false)
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
        binding.tvBucketName.text = bucketName
        androidx.core.view.ViewCompat.setAccessibilityHeading(binding.tvBucketName, true)
        binding.recyclerViewTransactions.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch {
            val allTransactions = repository.getSavingsTransactionsByBucket(bucketId).first()
            val visibleTransactions = if (isArchived) {
                allTransactions.filter { it.type != com.ataraxiagoddess.budgetbrewer.data.SavingsTransactionType.WITHDRAWAL }
            } else {
                allTransactions
            }

            // For archived buckets, no edit/delete callbacks → buttons hidden
            val adapter = TransactionHistoryAdapter(
                transactions = visibleTransactions,
                onEditClick = if (isArchived) null else onEditTransaction,
                onDeleteClick = if (isArchived) null else onDeleteTransaction
            )
            binding.recyclerViewTransactions.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
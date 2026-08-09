/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.ui.spending

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.SpendingEntry
import com.ataraxiagoddess.budgetbrewer.databinding.DialogSpendingEntryDetailBinding

class SpendingDetailDialogFragment(
    private val entry: SpendingEntry
) : DialogFragment() {

    init {
        setStyle(STYLE_NORMAL, R.style.AlertDialogTheme_BudgetBrewer)
    }

    private var _binding: DialogSpendingEntryDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSpendingEntryDetailBinding.inflate(inflater, container, false)
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

        // Dismiss dialog if both tag and note are empty
        if (entry.tag.isNullOrEmpty() && entry.note.isNullOrEmpty()) {
            dismiss()
            return
        }

        // Show tag section if tag exists and is not empty
        if (!entry.tag.isNullOrEmpty()) {
            binding.layoutTag.visibility = View.VISIBLE
            binding.tvTagText.text = entry.tag
        }

        // Show note section if note exists and is not empty
        if (!entry.note.isNullOrEmpty()) {
            binding.layoutNote.visibility = View.VISIBLE
            binding.tvNoteText.text = entry.note
        }

        // Close button dismisses the dialog
        binding.btnClose.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

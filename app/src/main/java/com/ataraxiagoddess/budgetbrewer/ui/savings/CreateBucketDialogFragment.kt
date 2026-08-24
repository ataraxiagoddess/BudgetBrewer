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
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucketType
import com.ataraxiagoddess.budgetbrewer.databinding.DialogCreateBucketBinding
import com.ataraxiagoddess.budgetbrewer.util.DecimalDigitsInputFilter
import com.ataraxiagoddess.budgetbrewer.util.SavingsBucketColors
import com.ataraxiagoddess.budgetbrewer.util.ValidationUtils
import java.util.UUID

class CreateBucketDialogFragment(
    private val onBucketCreated: (SavingsBucket) -> Unit,
    private val onShowSnackbar: (String) -> Unit
) : DialogFragment() {
    init {
        setStyle(STYLE_NORMAL, R.style.AlertDialogTheme_BudgetBrewer)
    }

    private var _binding: DialogCreateBucketBinding? = null
    private val binding get() = _binding!!

    private var selectedColorRes: Int = SavingsBucketColors.colors.first()
    private var bucketType: SavingsBucketType = SavingsBucketType.GOAL

    private lateinit var colorAdapter: ColorAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCreateBucketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // Fill 90% width and wrap height
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.editTextBucketName.requestFocus()
        binding.editTextBucketName.filters =
            arrayOf(
                ValidationUtils.getLengthFilter(ValidationUtils.MAX_LENGTH_NAME),
                ValidationUtils.getControlCharactersBlockFilter()
            )
        binding.editTextBucketName.setText("")
        binding.tvBucketNameCounter.text =
            getString(R.string.character_counter, 0, ValidationUtils.MAX_LENGTH_NAME)
        binding.editTextTargetAmount.filters = arrayOf(DecimalDigitsInputFilter())

        // Setup color RecyclerView with GridLayoutManager (4 columns)
        binding.recyclerViewColors.layoutManager = GridLayoutManager(requireContext(), 4)
        colorAdapter =
            ColorAdapter(requireContext(), SavingsBucketColors.colors.toList()) { position ->
                val oldPosition = colorAdapter.selectedPosition
                selectedColorRes = SavingsBucketColors.colors[position]
                colorAdapter.selectedPosition = position
                if (oldPosition != position) {
                    colorAdapter.notifyItemChanged(oldPosition)
                    colorAdapter.notifyItemChanged(position)
                }
            }
        binding.recyclerViewColors.adapter = colorAdapter

        // Radio group listener
        binding.radioGroupType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioGoal) {
                bucketType = SavingsBucketType.GOAL
                binding.editTextTargetAmount.visibility = View.VISIBLE
            } else {
                bucketType = SavingsBucketType.GROWTH
                binding.editTextTargetAmount.visibility = View.GONE
            }
            validateInputs()
        }

        // Text watchers for real-time validation
        val textWatcher =
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val name = binding.editTextBucketName.text.toString()
                    binding.tvBucketNameCounter.text =
                        getString(
                            R.string.character_counter,
                            name.length,
                            ValidationUtils.MAX_LENGTH_NAME
                        )
                    validateInputs()
                }
            }
        binding.editTextBucketName.addTextChangedListener(textWatcher)
        binding.editTextTargetAmount.addTextChangedListener(textWatcher)

        // Cancel button
        binding.buttonCancel.setOnClickListener { dismiss() }

        // Create button
        binding.buttonCreate.setOnClickListener {
            val name =
                binding.editTextBucketName.text
                    .toString()
                    .trim()
            if (!ValidationUtils.isValidName(name)) {
                onShowSnackbar(getString(R.string.bucket_name_required))
                return@setOnClickListener
            }

            val targetAmount: Double? =
                if (bucketType == SavingsBucketType.GOAL) {
                    binding.editTextTargetAmount.text
                        .toString()
                        .toDoubleOrNull()
                } else {
                    null
                }

            if (targetAmount != null && !ValidationUtils.isValidAmount(targetAmount)) {
                onShowSnackbar(getString(R.string.amount_exceeds_maximum))
                return@setOnClickListener
            }

            val bucket =
                SavingsBucket(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = bucketType,
                    target_amount = targetAmount,
                    color_hex = colorResToHex(selectedColorRes),
                    is_archived = false,
                    created_at = System.currentTimeMillis(),
                    updated_at = System.currentTimeMillis()
                )
            onBucketCreated(bucket)
            dismiss()
        }
    }

    private fun validateInputs() {
        val name =
            binding.editTextBucketName.text
                .toString()
                .trim()
        val isNameValid = ValidationUtils.isValidName(name)

        val isAmountValid =
            if (bucketType == SavingsBucketType.GOAL) {
                val amountStr = binding.editTextTargetAmount.text.toString()
                if (amountStr.isNotEmpty()) {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount != null && !ValidationUtils.isValidAmount(amount)) {
                        binding.tvTargetAmountError.text =
                            getString(R.string.amount_exceeds_maximum)
                        binding.tvTargetAmountError.visibility = View.VISIBLE
                        false
                    } else {
                        binding.tvTargetAmountError.visibility = View.INVISIBLE
                        true
                    }
                } else {
                    binding.tvTargetAmountError.visibility = View.INVISIBLE
                    true
                }
            } else {
                binding.tvTargetAmountError.visibility = View.INVISIBLE
                true
            }

        binding.buttonCreate.isEnabled = isNameValid && isAmountValid
    }

    private fun colorResToHex(colorRes: Int): String = try {
        val colorInt = ContextCompat.getColor(requireContext(), colorRes)
        String.format("#%06X", 0xFFFFFF and colorInt)
    } catch (_: Exception) {
        "#FF6B6B"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

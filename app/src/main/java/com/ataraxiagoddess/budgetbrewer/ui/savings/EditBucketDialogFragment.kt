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

class EditBucketDialogFragment(
    private val existingBucket: SavingsBucket,
    private val onBucketUpdated: (SavingsBucket) -> Unit,
    private val onShowSnackbar: (String) -> Unit
) : DialogFragment() {
    init {
        setStyle(STYLE_NORMAL, R.style.AlertDialogTheme_BudgetBrewer)
    }

    private var _binding: DialogCreateBucketBinding? = null
    private val binding get() = _binding!!

    private var selectedColorRes: Int = SavingsBucketColors.colors.first()
    private var bucketType: SavingsBucketType = existingBucket.type

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
        binding.editTextTargetAmount.filters = arrayOf(DecimalDigitsInputFilter())

        binding.tvBucketNameCounter.text =
            getString(
                R.string.character_counter,
                existingBucket.name.length,
                ValidationUtils.MAX_LENGTH_NAME
            )

        // Pre-fill data
        binding.editTextBucketName.setText(ValidationUtils.sanitizeString(existingBucket.name))
        if (existingBucket.type == SavingsBucketType.GOAL) {
            binding.radioGoal.isChecked = true
            binding.editTextTargetAmount.visibility = View.VISIBLE
            existingBucket.target_amount?.let {
                binding.editTextTargetAmount.setText(it.toString())
            }
        } else {
            binding.radioGrowth.isChecked = true
            binding.editTextTargetAmount.visibility = View.GONE
        }

        // Setup color grid
        binding.recyclerViewColors.layoutManager = GridLayoutManager(requireContext(), 4)

        colorAdapter =
            ColorAdapter(
                requireContext(),
                SavingsBucketColors.colors.toList(),
                onItemClicked = { position ->
                    val oldPosition = colorAdapter.selectedPosition
                    selectedColorRes = SavingsBucketColors.colors[position]
                    colorAdapter.selectedPosition = position
                    if (oldPosition != position) {
                        colorAdapter.notifyItemChanged(oldPosition)
                        colorAdapter.notifyItemChanged(position)
                    }
                }
            )
        binding.recyclerViewColors.adapter = colorAdapter

        // Pre‑select the bucket's existing color
        val colorHex = existingBucket.color_hex
        selectedColorRes = SavingsBucketColors.colors.firstOrNull {
            colorResToHex(it) == colorHex
        } ?: SavingsBucketColors.colors.first()
        val position = SavingsBucketColors.colors.indexOf(selectedColorRes)
        colorAdapter.selectedPosition = position
        if (position != -1) {
            colorAdapter.notifyItemChanged(position)
        }

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

        binding.buttonCancel.setOnClickListener { dismiss() }

        binding.buttonCreate.text = getString(R.string.save) // reuse button
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

            val updatedBucket =
                existingBucket.copy(
                    name = name,
                    type = bucketType,
                    target_amount = targetAmount,
                    color_hex = colorResToHex(selectedColorRes),
                    updated_at = System.currentTimeMillis()
                )
            onBucketUpdated(updatedBucket)
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

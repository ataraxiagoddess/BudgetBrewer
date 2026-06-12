package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.app.Dialog
import android.os.Bundle
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
import com.ataraxiagoddess.budgetbrewer.util.SavingsBucketColors

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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
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

        // Pre-fill data
        binding.editTextBucketName.setText(existingBucket.name)
        if (existingBucket.type == SavingsBucketType.GOAL) {
            binding.radioGoal.isChecked = true
            binding.editTextTargetAmount.visibility = View.VISIBLE
            existingBucket.target_amount?.let { binding.editTextTargetAmount.setText(it.toString()) }
        } else {
            binding.radioGrowth.isChecked = true
            binding.editTextTargetAmount.visibility = View.GONE
        }

        // Setup colour grid
        binding.recyclerViewColors.layoutManager = GridLayoutManager(requireContext(), 4)

        colorAdapter = ColorAdapter(
            requireContext(),
            SavingsBucketColors.colors.toList(),
            onItemClicked = { position ->
                selectedColorRes = SavingsBucketColors.colors[position]
                colorAdapter.selectedPosition = position
                colorAdapter.notifyDataSetChanged()
            }
        )
        binding.recyclerViewColors.adapter = colorAdapter

        // Pre‑select the bucket's existing color
        val colorHex = existingBucket.color_hex
        selectedColorRes = SavingsBucketColors.colors.firstOrNull {
            colorResToHex(it) == colorHex
        } ?: SavingsBucketColors.colors.first()
        colorAdapter.selectedPosition = SavingsBucketColors.colors.indexOf(selectedColorRes)
        colorAdapter.notifyDataSetChanged()

        binding.radioGroupType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioGoal) {
                bucketType = SavingsBucketType.GOAL
                binding.editTextTargetAmount.visibility = View.VISIBLE
            } else {
                bucketType = SavingsBucketType.GROWTH
                binding.editTextTargetAmount.visibility = View.GONE
            }
        }

        binding.buttonCancel.setOnClickListener { dismiss() }

        binding.buttonCreate.text = getString(R.string.save)  // reuse button
        binding.buttonCreate.setOnClickListener {
            val name = binding.editTextBucketName.text.toString().trim()
            if (name.isEmpty()) {
                onShowSnackbar(getString(R.string.bucket_name_required))
                return@setOnClickListener
            }

            val targetAmount: Double? = if (bucketType == SavingsBucketType.GOAL) {
                binding.editTextTargetAmount.text.toString().toDoubleOrNull()
            } else null

            val updatedBucket = existingBucket.copy(
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

    private fun colorResToHex(colorRes: Int): String {
        return try {
            val colorInt = ContextCompat.getColor(requireContext(), colorRes)
            String.format("#%06X", 0xFFFFFF and colorInt)
        } catch (e: Exception) {
            "#FF6B6B"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucketType
import com.ataraxiagoddess.budgetbrewer.databinding.DialogCreateBucketBinding
import com.ataraxiagoddess.budgetbrewer.util.SavingsBucketColors
import java.util.*

class CreateBucketDialogFragment(
    private val onBucketCreated: (SavingsBucket) -> Unit
) : DialogFragment() {

    private var _binding: DialogCreateBucketBinding? = null
    private val binding get() = _binding!!

    private var selectedColorRes: Int = SavingsBucketColors.colors.first()
    private var bucketType: SavingsBucketType = SavingsBucketType.GOAL

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCreateBucketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup color grid
        val colorAdapter = ColorAdapter(requireContext(), SavingsBucketColors.colors.toList())
        binding.gridViewColors.adapter = colorAdapter
        binding.gridViewColors.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            selectedColorRes = SavingsBucketColors.colors[position]
            colorAdapter.selectedPosition = position
            colorAdapter.notifyDataSetChanged()
        }

        // Radio group listener
        binding.radioGroupType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioGoal) {
                bucketType = SavingsBucketType.GOAL
                binding.editTextTargetAmount.visibility = View.VISIBLE
            } else {
                bucketType = SavingsBucketType.GROWTH
                binding.editTextTargetAmount.visibility = View.GONE
            }
        }

        // Cancel button
        binding.buttonCancel.setOnClickListener { dismiss() }

        // Create button
        binding.buttonCreate.setOnClickListener {
            val name = binding.editTextBucketName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.bucket_name_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val targetAmount: Double? = if (bucketType == SavingsBucketType.GOAL) {
                binding.editTextTargetAmount.text.toString().toDoubleOrNull()
            } else null

            val bucket = SavingsBucket(
                id = UUID.randomUUID().toString(),
                budget_id = "", // will be filled by repository
                name = name,
                type = bucketType,           // ← use "type", not "bucket_type"
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

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun colorResToHex(colorRes: Int): String {
        return try {
            val colorInt = ContextCompat.getColor(requireContext(), colorRes)
            String.format("#%06X", 0xFFFFFF and colorInt)
        } catch (e: Exception) {
            "#FF6B6B" // fallback
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
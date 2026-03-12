package com.ataraxiagoddess.budgetbrewer.ui.calendar

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.Expense
import com.ataraxiagoddess.budgetbrewer.data.Income
import com.ataraxiagoddess.budgetbrewer.data.SpendingEntry
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyDisplay
import timber.log.Timber

class DayDetailDialogFragment : DialogFragment() {

    private var unassignedIncomes: List<Income> = emptyList()
    private var onAssignIncome: ((Income) -> Unit)? = null

    companion object {
        private const val ARG_DAY = "day"
        private const val ARG_UNASSIGNED = "unassigned"

        fun newInstance(dayData: DayData, unassignedIncomes: List<Income>): DayDetailDialogFragment {
            val fragment = DayDetailDialogFragment()
            val args = Bundle().apply {
                putSerializable(ARG_DAY, dayData)
                putSerializable(ARG_UNASSIGNED, ArrayList(unassignedIncomes))
            }
            fragment.arguments = args
            return fragment
        }
    }

    data class DayData(
        val dayOfMonth: Int,
        val expenses: List<Expense>,
        val spendingEntries: List<SpendingEntry>,
        val assignedIncomes: List<Income>,
        val dayTotal: Double
    ) : java.io.Serializable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            @Suppress("UNCHECKED_CAST")
            unassignedIncomes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                it.getSerializable(ARG_UNASSIGNED, ArrayList::class.java) as? ArrayList<Income> ?: arrayListOf()
            } else {
                @Suppress("DEPRECATION")
                it.getSerializable(ARG_UNASSIGNED) as? ArrayList<Income> ?: arrayListOf()
            }
        }

        Timber.d("DayDetailModalDialogFragment: unassignedIncomes size = ${unassignedIncomes.size}")
        if (unassignedIncomes.isNotEmpty()) {
            Timber.d("First unassigned income: ${unassignedIncomes.first().sourceName}")
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_assign_income, null)

        val spinner = view.findViewById<Spinner>(R.id.spinnerIncome)
        val tvInstructions = view.findViewById<TextView>(R.id.tvInstructions)

        val exoRegular = ResourcesCompat.getFont(requireContext(), R.font.exo_regular)
        tvInstructions.typeface = exoRegular

        val adapter = object : ArrayAdapter<Income>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            unassignedIncomes
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                val income = getItem(position)
                view.text = income?.let { "${it.sourceName} (${it.amount.toCurrencyDisplay(resources)})" }
                view.typeface = exoRegular
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.spinner_dropdown_item, parent, false)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                val income = getItem(position)
                textView.text = income?.let { "${it.sourceName} (${it.amount.toCurrencyDisplay(resources)})" }
                textView.typeface = exoRegular
                textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_dialog))
                return view
            }
        }
        spinner.adapter = adapter

        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme_BudgetBrewer)
            .setCustomTitle(
                layoutInflater.inflate(R.layout.dialog_title, null).apply {
                    findViewById<TextView>(R.id.dialogTitle)?.apply {
                        text = getString(R.string.assign_income)
                        typeface = ResourcesCompat.getFont(requireContext(), R.font.exo_medium_italic)
                    }
                }
            )
            .setView(view)
            .setPositiveButton(R.string.assign, null)
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()

        dialog.setOnShowListener {
            val assignButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            // Do NOT set typeface here – theme handles it
            assignButton.isEnabled = adapter.count > 0

            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    assignButton.isEnabled = true
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    assignButton.isEnabled = false
                }
            }

            assignButton.setOnClickListener {
                val selectedIncome = spinner.selectedItem as Income
                onAssignIncome?.invoke(selectedIncome)
                dialog.dismiss()
                (parentFragment as? DayDetailModalDialogFragment)?.dismiss()
            }
        }
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background_rounded)

        return dialog
    }

    fun setOnAssignIncomeListener(listener: (Income) -> Unit) {
        onAssignIncome = listener
    }
}
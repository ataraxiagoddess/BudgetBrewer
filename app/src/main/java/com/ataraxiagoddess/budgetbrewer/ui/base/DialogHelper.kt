package com.ataraxiagoddess.budgetbrewer.ui.base

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ataraxiagoddess.budgetbrewer.R

@SuppressLint("InflateParams")
fun showBudgetBrewerDialog(
    inflater: LayoutInflater,
    context: Context,
    title: String,
    message: String? = null,
    view: View? = null,
    positiveButton: String = context.getString(R.string.ok),
    negativeButton: String? = context.getString(R.string.cancel),
    onPositive: () -> Unit = {},
    onNegative: () -> Unit = {}
): AlertDialog {
    val titleView = inflater.inflate(R.layout.dialog_title, null, false) as TextView
    titleView.text = title

    val builder = AlertDialog.Builder(context, R.style.AlertDialogTheme_BudgetBrewer)
        .setCustomTitle(titleView)

    when {
        message != null -> {
            val messageView = inflater.inflate(R.layout.dialog_message, null, false) as TextView
            messageView.text = message
            builder.setView(messageView)
        }
        view != null -> builder.setView(view)
    }

    builder.setPositiveButton(positiveButton) { _, _ -> onPositive() }

    if (negativeButton != null) {
        builder.setNegativeButton(negativeButton) { _, _ -> onNegative() }
    }

    return builder.create()
}
package com.ataraxiagoddess.budgetbrewer.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.RecurrenceType
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ExportHelper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val shortDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    // ------------------------------------------------------------------------
    // CSV Export
    // ------------------------------------------------------------------------
    suspend fun exportToCSV(context: Context): Uri? = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val csvContent = buildCSV(db)
        saveToDownloads(context, "BudgetBrewer_Export.csv", csvContent)
    }

    private suspend fun buildCSV(db: AppDatabase): ByteArray {
        val sb = StringBuilder()

        fun writeSection(title: String) {
            sb.append("\n\n").append(title).append("\n")
        }

        fun writeRow(vararg values: String) {
            sb.append(values.joinToString(",") { escapeCSV(it) }).append("\n")
        }

        // Helper to format currency
        fun formatAmount(amount: Double, currency: String) = "$currency$amount"

        // 1. Budgets
        writeSection("BUDGETS")
        writeRow("Month", "Year")
        db.budgetDao().getAllBudgetsSync().forEach { budget ->
            writeRow("${budget.month}/${budget.year}", budget.year.toString())
        }

        // 2. Incomes
        writeSection("INCOMES")
        writeRow("Budget Month", "Source", "Amount", "Frequency", "Tips?")
        db.incomeDao().getAllIncomesSync().forEach { inc ->
            val budget = db.budgetDao().getBudgetById(inc.budgetId)
            val monthYear = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            writeRow(monthYear, inc.sourceName, formatAmount(inc.amount, inc.currency),
                inc.frequency.name, if (inc.isTips) "Yes" else "No")
        }

        // 3. Expense Categories
        writeSection("EXPENSE CATEGORIES")
        writeRow("Budget Month", "Category Name")
        db.expenseCategoryDao().getAllCategoriesSync().forEach { cat ->
            val budget = db.budgetDao().getBudgetById(cat.budgetId)
            val monthYear = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            writeRow(monthYear, cat.name)
        }

        // 4. Expenses
        writeSection("EXPENSES")
        writeRow("Budget Month", "Category", "Description", "Amount", "Due Date", "Recurrence")
        db.expenseDao().getAllExpensesSync().forEach { exp ->
            val category = db.expenseCategoryDao().getCategoryById(exp.categoryId)
            val catName = category?.name ?: "Unknown"
            val budget = if (category != null) db.budgetDao().getBudgetById(category.budgetId) else null
            val monthYear = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            val recurrence = when (exp.recurrenceType) {
                RecurrenceType.NONE -> "One‑time"
                RecurrenceType.MONTHLY_SAME_DAY -> "Monthly"
                RecurrenceType.EVERY_X_DAYS -> "Every ${exp.recurrenceInterval} days"
            }
            writeRow(monthYear, catName, exp.description, formatAmount(exp.amount, "$"),
                shortDateFormat.format(Date(exp.dueDate)), recurrence)
        }

        // 5. Allocations
        writeSection("ALLOCATIONS")
        writeRow("Budget Month", "Savings", "Spending")
        db.allocationDao().getAllAllocationsSync().forEach { alloc ->
            val budget = db.budgetDao().getBudgetById(alloc.budgetId)
            val monthYear = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            val savings = if (alloc.savingsIsPercentage) "${alloc.savingsAmount}%" else formatAmount(alloc.savingsAmount, "$")
            val spending = if (alloc.spendingIsPercentage) "${alloc.spendingAmount}%" else formatAmount(alloc.spendingAmount, "$")
            writeRow(monthYear, savings, spending)
        }

        // 6. Daily Checklist – only days that actually have expenses
        writeSection("DAILY CHECKLIST")
        writeRow("Budget Month", "Day", "Checked?")
        val activeDays = getActiveDays(db)   // <-- replaced the long block with this
        db.dailyChecklistDao().getAllChecklistSync().forEach { item ->
            if (activeDays.contains(Pair(item.budgetId, item.dayOfMonth))) {
                val budget = db.budgetDao().getBudgetById(item.budgetId)
                val monthYear = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
                writeRow(monthYear, item.dayOfMonth.toString(), if (item.isChecked) "Yes" else "No")
            }
        }

        // 7. Spending Entries
        writeSection("SPENDING ENTRIES")
        writeRow("Budget Month", "Date", "Source", "Amount")
        db.spendingEntryDao().getAllSpendingEntriesSync().forEach { entry ->
            val budget = db.budgetDao().getBudgetById(entry.budgetId)
            val monthYear = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            writeRow(monthYear, shortDateFormat.format(Date(entry.date)), entry.source,
                formatAmount(entry.amount, "$"))
        }

        // 8. Month Settings
        writeSection("MONTH SETTINGS")
        writeRow("Budget Month", "Start Amount", "Overridden?")
        db.monthSettingsDao().getAllMonthSettingsSync().forEach { ms ->
            val budget = db.budgetDao().getBudgetById(ms.budgetId)
            val monthYear = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            writeRow(monthYear, formatAmount(ms.monthStartAmount, "$"), if (ms.monthStartOverridden) "Yes" else "No")
        }

        // 9. Daily Income Assignments
        writeSection("DAILY INCOME ASSIGNMENTS")
        writeRow("Budget Month", "Income Source", "Day")
        db.dailyIncomeAssignmentDao().getAllAssignmentsSync().forEach { dia ->
            val income = db.incomeDao().getIncomeById(dia.incomeId)
            val source = income?.sourceName ?: "Unknown"
            val budget = db.budgetDao().getBudgetById(dia.budgetId)
            val monthYear = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            writeRow(monthYear, source, dia.dayOfMonth.toString())
        }

        return sb.toString().toByteArray()
    }

    private fun escapeCSV(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }

    // ------------------------------------------------------------------------
    // PDF Export (simplified, readable layout)
    // ------------------------------------------------------------------------
    suspend fun exportToPDF(context: Context): Uri? = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val pdfBytes = buildPDF(context, db)
        saveToDownloads(context, "BudgetBrewer_Export.pdf", pdfBytes)
    }

    private suspend fun buildPDF(context: Context, db: AppDatabase): ByteArray {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 portrait
        val regularTypeface = ResourcesCompat.getFont(context, R.font.roboto_regular) ?: Typeface.DEFAULT
        val boldTypeface = ResourcesCompat.getFont(context, R.font.roboto_bold) ?: Typeface.DEFAULT_BOLD
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            color = Color.BLACK
            typeface = boldTypeface
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f
            color = Color.BLACK
            typeface = boldTypeface
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.DKGRAY
            typeface = regularTypeface
        }

        val leftMargin = 40f
        val indent = 20f
        var y = 40f
        val lineHeight = 16f

        fun checkNewPage() {
            if (y > pageInfo.pageHeight - 60) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 40f
            }
        }

        // Title
        canvas.drawText("Budget Brewer Export", leftMargin, y, titlePaint)
        y += lineHeight * 1.5f
        canvas.drawText("Generated: ${dateFormat.format(Date())}", leftMargin, y, textPaint)
        y += lineHeight * 2f

        // Helper to write a section
        fun writeSection(title: String, content: List<String>) {
            checkNewPage()
            canvas.drawText(title, leftMargin, y, headingPaint)
            y += lineHeight
            content.forEach { line ->
                checkNewPage()
                canvas.drawText("• $line", leftMargin + indent, y, textPaint)
                y += lineHeight
            }
            y += lineHeight
        }

        // Build content lists
        val budgets = db.budgetDao().getAllBudgetsSync().map { "${it.month}/${it.year}" }
        writeSection("BUDGETS", budgets)

        val incomes = db.incomeDao().getAllIncomesSync().map { inc ->
            val budget = db.budgetDao().getBudgetById(inc.budgetId)
            val month = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            "${inc.sourceName}: ${inc.currency}${inc.amount} (${inc.frequency}) - ${if (inc.isTips) "Tips" else "Regular"} – $month"
        }
        writeSection("INCOMES", incomes)

        val categories = db.expenseCategoryDao().getAllCategoriesSync().map { cat ->
            val budget = db.budgetDao().getBudgetById(cat.budgetId)
            val month = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            "${cat.name} – $month"
        }
        writeSection("EXPENSE CATEGORIES", categories)

        val expenses = db.expenseDao().getAllExpensesSync().map { exp ->
            val cat = db.expenseCategoryDao().getCategoryById(exp.categoryId)
            val catName = cat?.name ?: "Unknown"
            val budget = if (cat != null) db.budgetDao().getBudgetById(cat.budgetId) else null
            val month = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            val recurrence = when (exp.recurrenceType) {
                RecurrenceType.NONE -> "One‑time"
                RecurrenceType.MONTHLY_SAME_DAY -> "Monthly"
                RecurrenceType.EVERY_X_DAYS -> "Every ${exp.recurrenceInterval} days"
            }
            "${exp.description}: $${exp.amount} due ${shortDateFormat.format(Date(exp.dueDate))} (${recurrence}) – $catName – $month"
        }
        writeSection("EXPENSES", expenses)

        val allocations = db.allocationDao().getAllAllocationsSync().map { alloc ->
            val budget = db.budgetDao().getBudgetById(alloc.budgetId)
            val month = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            val savings = if (alloc.savingsIsPercentage) "${alloc.savingsAmount}%" else "$${alloc.savingsAmount}"
            val spending = if (alloc.spendingIsPercentage) "${alloc.spendingAmount}%" else "$${alloc.spendingAmount}"
            "Savings: $savings, Spending: $spending – $month"
        }
        writeSection("ALLOCATIONS", allocations)

        val spendingEntries = db.spendingEntryDao().getAllSpendingEntriesSync().map { entry ->
            val budget = db.budgetDao().getBudgetById(entry.budgetId)
            val month = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            "${shortDateFormat.format(Date(entry.date))}: ${entry.source} – $${entry.amount} – $month"
        }
        writeSection("SPENDING ENTRIES", spendingEntries)

        val activeDays = getActiveDays(db)
        val checklist = db.dailyChecklistDao().getAllChecklistSync()
            .filter { item -> activeDays.contains(Pair(item.budgetId, item.dayOfMonth)) }
            .map { item ->
                val budget = db.budgetDao().getBudgetById(item.budgetId)
                val month = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
                "Day ${item.dayOfMonth}: ${if (item.isChecked) "✓" else "✗"} – $month"
            }
        writeSection("DAILY CHECKLIST", checklist)

        val monthSettings = db.monthSettingsDao().getAllMonthSettingsSync().map { ms ->
            val budget = db.budgetDao().getBudgetById(ms.budgetId)
            val month = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            "Start amount: $${ms.monthStartAmount} (overridden: ${if (ms.monthStartOverridden) "Yes" else "No"}) – $month"
        }
        writeSection("MONTH SETTINGS", monthSettings)

        val incomeAssignments = db.dailyIncomeAssignmentDao().getAllAssignmentsSync().map { dia ->
            val income = db.incomeDao().getIncomeById(dia.incomeId)
            val source = income?.sourceName ?: "Unknown"
            val budget = db.budgetDao().getBudgetById(dia.budgetId)
            val month = if (budget != null) "${budget.month}/${budget.year}" else "Unknown"
            "$source assigned to day ${dia.dayOfMonth} – $month"
        }
        writeSection("DAILY INCOME ASSIGNMENTS", incomeAssignments)

        document.finishPage(page)
        val stream = ByteArrayOutputStream()
        document.writeTo(stream)
        document.close()
        return stream.toByteArray()
    }

    // ------------------------------------------------------------------------
    // Save to Downloads
    // ------------------------------------------------------------------------
    private fun saveToDownloads(context: Context, fileName: String, data: ByteArray): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ – use MediaStore
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, if (fileName.endsWith(".csv")) "text/csv" else "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { stream ->
                    stream.write(data)
                    return uri
                }
            }
            null
        } else {
            // Android 9 and below – use external storage and FileProvider
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            try {
                FileOutputStream(file).use { stream ->
                    stream.write(data)
                }
                // Return a content URI via FileProvider
                return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }

    fun shareFile(context: Context, uri: Uri, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (uri.toString().endsWith(".csv")) "text/csv" else "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    private fun getDayOfMonth(timestamp: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(Calendar.DAY_OF_MONTH)
    }

    private suspend fun getActiveDays(db: AppDatabase): Set<Pair<String, Int>> {
        val allExpenses = db.expenseDao().getAllExpensesSync()
        val activeDays = mutableSetOf<Pair<String, Int>>()
        allExpenses.forEach { expense ->
            val category = db.expenseCategoryDao().getCategoryById(expense.categoryId)
            if (category != null) {
                val day = getDayOfMonth(expense.dueDate)
                activeDays.add(Pair(category.budgetId, day))
            }
        }
        return activeDays
    }
}
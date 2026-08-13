/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.RecurrenceType
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

    // ========================================================================
    // Budget‑scoped CSV export
    // ========================================================================
    suspend fun exportBudgetToCSV(
        context: Context,
        budgetId: String,
        monthLabel: String,
    ): Uri? =
        withContext(Dispatchers.IO) {
            CurrencyPrefs.init(context)
            val db = AppDatabase.getDatabase(context)
            val csvContent = buildCSVForBudget(db, budgetId)
            saveToDownloads(context, "BudgetBrewer_$monthLabel.csv", csvContent)
        }

    private suspend fun buildCSVForBudget(
        db: AppDatabase,
        budgetId: String,
    ): ByteArray {
        val sb = StringBuilder()
        val locale = Locale.getDefault()

        fun writeSection(title: String) {
            sb.append("\n\n").append(title).append("\n")
        }

        fun writeRow(vararg values: String) {
            sb.append(values.joinToString(",") { escapeCSV(it) }).append("\n")
        }

        fun formatCurrent(amount: Double) = CurrencyPrefs.format(amount, locale)

        fun formatWithCurrency(
            amount: Double,
            currencyValue: String,
        ) = CurrencyPrefs.formatWithCurrency(amount, currencyValue, locale)

        val budget = db.budgetDao().getBudgetById(budgetId) ?: return byteArrayOf()
        val monthYear = "${budget.month}/${budget.year}"

        // Budgets
        writeSection("BUDGET")
        writeRow("Month", "Year")
        writeRow(monthYear, budget.year.toString())

        // Incomes
        writeSection("INCOMES")
        writeRow("Source", "Amount", "Frequency", "Tips?")
        db.incomeDao().getIncomesForBudget(budgetId).first().forEach { inc ->
            writeRow(
                inc.sourceName,
                formatWithCurrency(inc.amount, inc.currency),
                inc.frequency.name,
                if (inc.isTips) "Yes" else "No",
            )
        }

        // Categories
        writeSection("EXPENSE CATEGORIES")
        writeRow("Category Name")
        db.expenseCategoryDao().getCategoriesForBudget(budgetId).first().forEach { cat ->
            writeRow(cat.name)
        }

        // Expenses
        writeSection("EXPENSES")
        writeRow("Category", "Description", "Amount", "Due Date", "Recurrence")
        db
            .expenseDao()
            .getExpensesForBudget(budgetId)
            .first()
            .sortedBy { it.dueDate }
            .forEach { exp ->
                val category = db.expenseCategoryDao().getCategoryById(exp.categoryId)
                val catName = category?.name ?: "Unknown"
                val recurrence =
                    when (exp.recurrenceType) {
                        RecurrenceType.NONE -> "One-time"
                        RecurrenceType.MONTHLY_SAME_DAY -> "Monthly"
                        RecurrenceType.EVERY_X_DAYS -> "Every ${exp.recurrenceInterval} days"
                    }
                writeRow(
                    catName,
                    exp.description,
                    formatCurrent(exp.amount),
                    shortDateFormat.format(Date(exp.dueDate)),
                    recurrence,
                )
            }

        // Allocations
        writeSection("ALLOCATIONS")
        writeRow("Savings", "Spending")
        db.allocationDao().getAllocationForBudget(budgetId).first()?.let { alloc ->
            val savings = if (alloc.savingsIsPercentage) "${alloc.savingsAmount}%" else formatCurrent(alloc.savingsAmount)
            val spending = if (alloc.spendingIsPercentage) "${alloc.spendingAmount}%" else formatCurrent(alloc.spendingAmount)
            writeRow(savings, spending)
        }

        // Savings Buckets
        writeSection("SAVINGS BUCKETS")
        writeRow("Bucket Name", "Type", "Target", "Current", "Archived")
        val allBuckets = db.savingsBucketDao().getAllBucketsSync()
        allBuckets.forEach { bucket ->
            val target = bucket.target_amount?.let { formatCurrent(it) } ?: ""
            writeRow(
                bucket.name,
                bucket.type.name,
                target,
                formatCurrent(bucket.current_amount),
                if (bucket.is_archived) "Yes" else "No",
            )
        }

        // Savings Transactions
        writeSection("SAVINGS TRANSACTIONS")
        writeRow("Bucket Name", "Amount", "Date", "Type")
        val bucketMap = allBuckets.associateBy { it.id }
        db
            .savingsTransactionDao()
            .getAllTransactionsSync()
            .sortedBy { it.date }
            .forEach { tx ->
                val bucketName = bucketMap[tx.bucket_id]?.name ?: "Unknown"
                writeRow(
                    bucketName,
                    formatCurrent(tx.amount),
                    shortDateFormat.format(Date(tx.date)),
                    tx.type.name,
                )
            }

        // Spending Entries
        writeSection("SPENDING ENTRIES")
        writeRow("Date", "Source", "Amount", "Tag", "Note")
        db
            .spendingEntryDao()
            .getSpendingEntriesForBudget(budgetId)
            .first()
            .sortedBy { it.date }
            .forEach { entry ->
                writeRow(
                    shortDateFormat.format(Date(entry.date)),
                    entry.source,
                    formatCurrent(entry.amount),
                    entry.tag ?: "",
                    entry.note ?: "",
                )
            }

        // Daily Checklist
        writeSection("DAILY CHECKLIST")
        writeRow("Day", "Checked?")
        val activeDays = getActiveDaysForBudget(db, budgetId)
        db
            .dailyChecklistDao()
            .getChecklistForBudget(budgetId)
            .first()
            .filter { activeDays.contains(Pair(it.budgetId, it.dayOfMonth)) }
            .sortedBy { it.dayOfMonth }
            .forEach { item ->
                writeRow(item.dayOfMonth.toString(), if (item.isChecked) "Yes" else "No")
            }

        // Month Settings
        writeSection("MONTH SETTINGS")
        writeRow("Start Amount", "Overridden?")
        db.monthSettingsDao().getSettingsForBudget(budgetId).first()?.let { ms ->
            writeRow(formatCurrent(ms.monthStartAmount), if (ms.monthStartOverridden) "Yes" else "No")
        }

        // Daily Income Assignments
        writeSection("DAILY INCOME ASSIGNMENTS")
        writeRow("Income Source", "Day")
        db
            .dailyIncomeAssignmentDao()
            .getAssignmentsForBudget(budgetId)
            .first()
            .sortedBy { it.dayOfMonth }
            .forEach { dia ->
                val income = db.incomeDao().getIncomeById(dia.incomeId)
                val source = income?.sourceName ?: "Unknown"
                writeRow(source, dia.dayOfMonth.toString())
            }

        return sb.toString().toByteArray()
    }

    private fun escapeCSV(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    // ========================================================================
    // Budget‑scoped PDF export (beautified with tables and branded header)
    // ========================================================================
    suspend fun exportBudgetToPDF(
        context: Context,
        budgetId: String,
        monthLabel: String,
    ): Uri? =
        withContext(Dispatchers.IO) {
            CurrencyPrefs.init(context)
            val db = AppDatabase.getDatabase(context)
            val pdfBytes = buildPDFForBudget(context, db, budgetId)
            saveToDownloads(context, "BudgetBrewer_$monthLabel.pdf", pdfBytes)
        }

    private suspend fun buildPDFForBudget(
        context: Context,
        db: AppDatabase,
        budgetId: String,
    ): ByteArray {
        val document = PdfDocument()
        val locale = Locale.getDefault()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()

        val regularTypeface = ResourcesCompat.getFont(context, R.font.exo_regular) ?: Typeface.DEFAULT
        val boldTypeface = ResourcesCompat.getFont(context, R.font.exo_semi_bold) ?: Typeface.DEFAULT_BOLD

        val teal = ContextCompat.getColor(context, R.color.bg_main)
        val lavender = ContextCompat.getColor(context, R.color.bg_container)
        val darkText = "#2E2E2E".toColorInt()

        val titlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = ResourcesCompat.getFont(context, R.font.blkchcry) ?: boldTypeface
                textSize = 36f
                color = Color.WHITE
                isFakeBoldText = true
            }
        val subtitlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = regularTypeface
                textSize = 18f
                color = Color.WHITE
            }
        val headingPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = boldTypeface
                textSize = 13f
                color = darkText
            }
        val cellPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = regularTypeface
                textSize = 10f
                color = darkText
            }
        val linePaint =
            Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 0.5f
            }
        val borderPaint =
            Paint().apply {
                color = teal
                strokeWidth = 4f
                style = Paint.Style.STROKE
            }

        val options =
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

        val originalLogo = BitmapFactory.decodeResource(context.resources, R.drawable.budget_brewer_logo, options)

        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        fun formatCurrent(amount: Double) = CurrencyPrefs.format(amount, locale)

        fun formatWithCurrency(
            amount: Double,
            currencyValue: String,
        ) = CurrencyPrefs.formatWithCurrency(amount, currencyValue, locale)

        val margin = 30f
        val tableStartX = margin + 15f
        val usableWidth = pageInfo.pageWidth - 2 * margin

        // Load the budget
        val budget = db.budgetDao().getBudgetById(budgetId) ?: return byteArrayOf()

        // Initialize first page
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        canvas.drawRect(margin, margin, pageInfo.pageWidth - margin, pageInfo.pageHeight - margin, borderPaint)

        // Header background (same on every page)
        val headerBgPaint =
            Paint().apply {
                color = teal
                style = Paint.Style.FILL
            }

        // ---- Page‑header drawing lambda (used for first page and page breaks) ----
        fun drawPageHeader(
            canvas: Canvas,
            startingY: Float,
        ): Float {
            var yPos = startingY
            // Teal strip
            canvas.drawRect(margin, yPos, pageInfo.pageWidth - margin, yPos + 80f, headerBgPaint)
            // Titles
            canvas.drawText("Budget Brewer", margin + 20f, yPos + 45f, titlePaint)
            canvas.drawText("${budget.month}/${budget.year}", margin + 20f, yPos + 70f, subtitlePaint)
            // Logo
            val logoWidth = 60f
            val logoHeight = 60f
            val logoX = pageInfo.pageWidth - margin - 20f - logoWidth
            val logoY = yPos + (80f - logoHeight) / 2f

            val srcRect = Rect(0, 0, originalLogo.width, originalLogo.height)
            val dstRect = RectF(logoX, logoY, logoX + logoWidth, logoY + logoHeight)

            canvas.drawBitmap(originalLogo, srcRect, dstRect, logoPaint)

            yPos += 80f
            yPos += 20f
            return yPos
        }

        var y = drawPageHeader(canvas, margin)

        // ===== TABLE HELPERS =====
        fun drawTableHeader(cols: List<Pair<String, Float>>) {
            val totalWeight = cols.sumOf { it.second.toDouble() }.toFloat()
            var x = tableStartX
            val headerRectPaint =
                Paint().apply {
                    color = lavender
                    style = Paint.Style.FILL
                }
            canvas.drawRect(tableStartX - 5f, y, tableStartX + usableWidth - 25f, y + 22f, headerRectPaint)
            for ((label, weight) in cols) {
                val colWidth = (usableWidth - 30f) * (weight / totalWeight)
                canvas.drawText(label, x + 3f, y + 15f, headingPaint)
                x += colWidth + 10f
            }
            y += 24f
        }

        fun drawRow(
            cells: List<String>,
            cols: List<Pair<String, Float>>,
            isAlternate: Boolean,
        ) {
            if (isAlternate) {
                val altPaint =
                    Paint().apply {
                        color = lavender
                        style = Paint.Style.FILL
                        alpha = 80
                    }
                canvas.drawRect(tableStartX - 5f, y, tableStartX + usableWidth - 25f, y + 20f, altPaint)
            }
            val totalWeight = cols.sumOf { it.second.toDouble() }.toFloat()
            var x = tableStartX
            for ((cell, col) in cells.zip(cols)) {
                val colWidth = (usableWidth - 30f) * (col.second / totalWeight)
                canvas.drawText(cell, x + 3f, y + 14f, cellPaint)
                x += colWidth + 10f
            }
            y += 18f
            canvas.drawLine(tableStartX - 5f, y, tableStartX + usableWidth - 25f, y, linePaint)
        }

        fun checkNewPage() {
            if (y > pageInfo.pageHeight - margin - 60f) {
                drawFooter(canvas, pageInfo.pageWidth - margin, pageInfo.pageHeight - margin, teal, regularTypeface)
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                canvas.drawRect(margin, margin, pageInfo.pageWidth - margin, pageInfo.pageHeight - margin, borderPaint)
                y = drawPageHeader(canvas, margin) // full header on new page
            }
        }

        fun drawSection(
            title: String,
            cols: List<Pair<String, Float>>,
            rows: List<List<String>>,
        ) {
            checkNewPage()
            val sectionPaint =
                Paint().apply {
                    color = teal
                    style = Paint.Style.FILL
                }
            canvas.drawRect(tableStartX - 5f, y, tableStartX + usableWidth - 25f, y + 24f, sectionPaint)
            val sectionTextPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = boldTypeface
                    textSize = 14f
                    color = Color.WHITE
                }
            canvas.drawText(title, tableStartX + 5f, y + 17f, sectionTextPaint)
            y += 28f
            drawTableHeader(cols)
            rows.forEachIndexed { idx, row ->
                checkNewPage()
                drawRow(row, cols, idx % 2 == 1)
            }
            y += 10f
        }

        // ===== SECTIONS (unchanged) =====
        val budgetRows = listOf(listOf("${budget.month}/${budget.year}", budget.year.toString()))
        drawSection("BUDGET", listOf("Month" to 1f, "Year" to 1f), budgetRows)

        val incomeRows =
            db.incomeDao().getIncomesForBudget(budgetId).first().map { inc ->
                listOf(
                    inc.sourceName,
                    formatWithCurrency(inc.amount, inc.currency),
                    inc.frequency.name,
                    if (inc.isTips) "Yes" else "No",
                )
            }
        drawSection("INCOMES", listOf("Source" to 1.5f, "Amount" to 1f, "Frequency" to 0.8f, "Tips?" to 0.5f), incomeRows)

        val catRows =
            db
                .expenseCategoryDao()
                .getCategoriesForBudget(budgetId)
                .first()
                .map { listOf(it.name) }
        drawSection("EXPENSE CATEGORIES", listOf("Category Name" to 2f), catRows)

        val expenseRows =
            db
                .expenseDao()
                .getExpensesForBudget(budgetId)
                .first()
                .sortedBy { it.dueDate }
                .map { exp ->
                    val category = db.expenseCategoryDao().getCategoryById(exp.categoryId)
                    val catName = category?.name ?: "Unknown"
                    val recurrence =
                        when (exp.recurrenceType) {
                            RecurrenceType.NONE -> "One‑time"
                            RecurrenceType.MONTHLY_SAME_DAY -> "Monthly"
                            RecurrenceType.EVERY_X_DAYS -> "Every ${exp.recurrenceInterval} days"
                        }
                    listOf(
                        catName,
                        exp.description,
                        formatCurrent(exp.amount),
                        shortDateFormat.format(Date(exp.dueDate)),
                        recurrence,
                    )
                }
        drawSection(
            "EXPENSES",
            listOf(
                "Category" to 1f,
                "Description" to 2f,
                "Amount" to 1f,
                "Due Date" to 1.5f,
                "Recurrence" to 1.5f,
            ),
            expenseRows,
        )

        val allocRows =
            db.allocationDao().getAllocationForBudget(budgetId).first()?.let { alloc ->
                val savings = if (alloc.savingsIsPercentage) "${alloc.savingsAmount}%" else formatCurrent(alloc.savingsAmount)
                val spending = if (alloc.spendingIsPercentage) "${alloc.spendingAmount}%" else formatCurrent(alloc.spendingAmount)
                listOf(listOf(savings, spending))
            } ?: emptyList()
        if (allocRows.isNotEmpty()) {
            drawSection("ALLOCATIONS", listOf("Savings" to 1f, "Spending" to 1f), allocRows)
        }

        val allBuckets = db.savingsBucketDao().getAllBucketsSync()
        val bucketRows =
            allBuckets.map { bucket ->
                val target = bucket.target_amount?.let { formatCurrent(it) } ?: ""
                listOf(
                    bucket.name,
                    bucket.type.name,
                    target,
                    formatCurrent(bucket.current_amount),
                    if (bucket.is_archived) "Yes" else "No",
                )
            }
        drawSection(
            "SAVINGS BUCKETS",
            listOf(
                "Bucket Name" to 2f,
                "Type" to 1f,
                "Target" to 1f,
                "Current" to 1f,
                "Archived" to 1f,
            ),
            bucketRows,
        )

        val txRows =
            db
                .savingsTransactionDao()
                .getAllTransactionsSync()
                .sortedBy { it.date }
                .map { tx ->
                    val bucketName = allBuckets.associateBy { it.id }[tx.bucket_id]?.name ?: "Unknown"
                    listOf(bucketName, formatCurrent(tx.amount), shortDateFormat.format(Date(tx.date)), tx.type.name)
                }
        drawSection(
            "SAVINGS TRANSACTIONS",
            listOf(
                "Bucket Name" to 2f,
                "Amount" to 1f,
                "Date" to 1.5f,
                "Type" to 1f,
            ),
            txRows,
        )

        val spendingRows =
            db
                .spendingEntryDao()
                .getSpendingEntriesForBudget(budgetId)
                .first()
                .sortedBy { it.date }
                .map { entry ->
                    listOf(
                        shortDateFormat.format(Date(entry.date)),
                        entry.source,
                        formatCurrent(entry.amount),
                        entry.tag ?: "",
                        entry.note ?: "",
                    )
                }
        drawSection("SPENDING ENTRIES", listOf("Date" to 1f, "Source" to 1.2f, "Amount" to 1f, "Tag" to 1f, "Note" to 1.5f), spendingRows)

        val activeDays = getActiveDaysForBudget(db, budgetId)
        val checklistRows =
            db
                .dailyChecklistDao()
                .getChecklistForBudget(budgetId)
                .first()
                .filter { activeDays.contains(Pair(it.budgetId, it.dayOfMonth)) }
                .sortedBy { it.dayOfMonth }
                .map { listOf(it.dayOfMonth.toString(), if (it.isChecked) "Yes" else "No") }
        drawSection("DAILY CHECKLIST", listOf("Day" to 1f, "Checked?" to 1f), checklistRows)

        val monthSettingRows =
            db.monthSettingsDao().getSettingsForBudget(budgetId).first()?.let { ms ->
                listOf(listOf(formatCurrent(ms.monthStartAmount), if (ms.monthStartOverridden) "Yes" else "No"))
            } ?: emptyList()
        if (monthSettingRows.isNotEmpty()) {
            drawSection("MONTH SETTINGS", listOf("Start Amount" to 1f, "Overridden?" to 1f), monthSettingRows)
        }

        val diaRows =
            db
                .dailyIncomeAssignmentDao()
                .getAssignmentsForBudget(budgetId)
                .first()
                .sortedBy { it.dayOfMonth }
                .map { dia ->
                    val income = db.incomeDao().getIncomeById(dia.incomeId)
                    val source = income?.sourceName ?: "Unknown"
                    listOf(source, dia.dayOfMonth.toString())
                }
        drawSection("DAILY INCOME ASSIGNMENTS", listOf("Income Source" to 2f, "Day" to 1f), diaRows)

        drawFooter(canvas, pageInfo.pageWidth - margin, pageInfo.pageHeight - margin, teal, regularTypeface)

        document.finishPage(page)
        val stream = ByteArrayOutputStream()
        document.writeTo(stream)
        document.close()
        return stream.toByteArray()
    }

    // ========================================================================
    // Helper functions
    // ========================================================================
    private fun drawFooter(
        canvas: Canvas,
        right: Float,
        bottom: Float,
        teal: Int,
        font: Typeface,
    ) {
        val footerPaint =
            Paint().apply {
                color = teal
                style = Paint.Style.FILL
            }
        val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = font
                textSize = 10f
                color = Color.WHITE
            }
        canvas.drawRect(30f, bottom - 30f, right, bottom, footerPaint)
        canvas.drawText("Budget Brewer – Your zero‑dollar budget companion", 50f, bottom - 10f, textPaint)
        val generatedText = "Generated: ${dateFormat.format(Date())}"
        val textWidth = textPaint.measureText(generatedText)
        canvas.drawText(generatedText, right - textWidth - 20f, bottom - 10f, textPaint)
    }

    private fun saveToDownloads(
        context: Context,
        fileName: String,
        data: ByteArray,
    ): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (fileName.endsWith(".csv")) "text/csv" else "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { stream -> stream.write(data) }
                return uri
            }
            null
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            try {
                FileOutputStream(file).use { stream -> stream.write(data) }
                return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }

    fun shareFile(
        context: Context,
        uri: Uri,
        title: String,
    ) {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
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

    private suspend fun getActiveDaysForBudget(
        db: AppDatabase,
        budgetId: String,
    ): Set<Pair<String, Int>> {
        val expenses = db.expenseDao().getExpensesForBudget(budgetId).first()
        val activeDays = mutableSetOf<Pair<String, Int>>()
        expenses.forEach { expense ->
            val day = getDayOfMonth(expense.dueDate)
            activeDays.add(Pair(budgetId, day))
        }
        return activeDays
    }
}

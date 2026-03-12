package com.ataraxiagoddess.budgetbrewer.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ataraxiagoddess.budgetbrewer.data.SyncWorker
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

object SyncHelper {
    suspend fun triggerSyncIfNeeded(context: Context) {
        val db = AppDatabase.getDatabase(context)
        val pendingCount = withContext(Dispatchers.IO) {
            db.pendingSyncDao().getAllPendingSync().size
        }
        if (pendingCount > 0) {
            Timber.d("Triggering instant sync for $pendingCount pending items")
            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "instant_sync",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
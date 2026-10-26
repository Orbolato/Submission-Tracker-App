package org.manuscriptwatch.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val chosenId = inputData.getString("onlyId")
        val items = SubmissionStore.all(applicationContext).filter { chosenId == null || it.id == chosenId }
        for (item in items) {
            if (isStopped) break
            if (item.statusUrl.isBlank()) continue
            val result = try {
                if (JournalPageClassifier.isSpringerSubmissionDetail(item.statusUrl))
                    SpringerWebMonitor.check(applicationContext, item)
                else SiteMonitor.check(item)
            } catch (e: Exception) {
                ExtractResult.NeedsAttention("Network or parsing error: ${e.javaClass.simpleName}")
            }
            when (result) {
                is ExtractResult.Found -> {
                    val change = SubmissionStore.observe(applicationContext, item.id, result.status,
                        if (JournalPageClassifier.isSpringerSubmissionDetail(item.statusUrl))
                            "Springer rendered webpage" else "Journal webpage")
                    if (change != null) AppNotifications.notifyChange(applicationContext, change)
                }
                is ExtractResult.NeedsAttention -> SubmissionStore.updateError(applicationContext, item.id, result.reason)
            }
        }
        Result.success()
    }
}

object MonitorScheduler {
    private const val WORK_NAME = "journal_status_checks"
    fun schedule(context: Context) {
        val hours = SubmissionStore.intervalHours(context)
        val constraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<MonitorWorker>(hours, TimeUnit.HOURS)
            .setConstraints(constraint)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }
    fun checkNow(context: Context, id: String? = null) {
        val constraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<MonitorWorker>()
            .setConstraints(constraint)
            .apply {
                if (id != null) setInputData(androidx.work.workDataOf("onlyId" to id))
            }.build()
        WorkManager.getInstance(context).enqueue(request)
    }
}

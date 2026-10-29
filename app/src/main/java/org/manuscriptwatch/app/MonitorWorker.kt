package org.manuscriptwatch.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val periodic = inputData.getBoolean("periodicCheck", false)
        val chosenId = inputData.getString("onlyId")
        val chosenSourceUrl = inputData.getString("onlySourceUrl")
        val items = SubmissionStore.all(applicationContext).filter { chosenId == null || it.id == chosenId }
        var transientFailure = false
        var anyUnverified = false

        for (item in items) {
            if (isStopped) return@withContext Result.retry()
            var found = false
            var sourceFailed = false
            val errors = mutableListOf<String>()
            var discovered: EditorialManagerDiscovery.Found? = null
            val isEditorial = listOf(item.editorialMenuUrl, item.journalUrl, item.statusUrl)
                .any { EditorialManagerDiscovery.isEditorialManager(it) }
            if (isEditorial && chosenSourceUrl == null) {
                when (val discovery = EditorialManagerDiscovery.locate(item)) {
                    is EditorialManagerDiscovery.Result.Located -> {
                        discovered = discovery.value
                        val endpoint = TrackingSource(TrackingKind.EDITORIAL_MANAGER,
                            discovery.value.url, folder = discovery.value.folder)
                        SubmissionStore.observeSource(applicationContext, item.id,
                            endpoint, discovery.value.status, discovery.value.folder)
                            .forEach { AppNotifications.notifyChange(applicationContext, it) }
                        found = true
                    }
                    is EditorialManagerDiscovery.Result.NeedsAttention -> errors += discovery.reason
                }
            }
            val sources = TrackingSources.effective(item)
            for (source in sources.take(8).filter { chosenSourceUrl == null || it.url == chosenSourceUrl }) {
                if (isStopped) return@withContext Result.retry()
                // A newly discovered folder supersedes the old Editorial Manager folder.
                if (source.kind == TrackingKind.EDITORIAL_MANAGER && discovered != null) continue
                val result = try {
                    when (source.kind) {
                        TrackingKind.SPRINGER -> SpringerWebMonitor.check(applicationContext, item.copy(statusUrl = source.url))
                        TrackingKind.ELSEVIER_HUB, TrackingKind.ELSEVIER_PRODUCTION ->
                            ElsevierWebMonitor.check(applicationContext, source)
                        else -> SiteMonitor.check(item.copy(statusUrl = source.url))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A one-off network/WebView failure should not be silently marked successful.
                    ExtractResult.NeedsAttention("Check failed (${e.javaClass.simpleName}).", retryable = true)
                }
                when (result) {
                    is ExtractResult.Found -> {
                        // Verified includes the case in which the website status has not changed.
                        SubmissionStore.observeSource(applicationContext, item.id, source, result)
                            .forEach { AppNotifications.notifyChange(applicationContext, it) }
                        found = true
                    }
                    is ExtractResult.NeedsAttention -> {
                        result.reviewProgress?.let {
                            SubmissionStore.observeReviewProgress(applicationContext, item.id, source, it)
                        }
                        SubmissionStore.recordSourceError(applicationContext, item.id, source, result.reason)
                        errors += "${source.kind.title}: ${result.reason}"
                        sourceFailed = true
                        if (result.retryable) transientFailure = true
                    }
                }
            }
            if (!found) {
                SubmissionStore.updateError(applicationContext, item.id,
                    errors.firstOrNull() ?: "No tracking source is configured. Open the journal or add a tracking link.")
                anyUnverified = true
            }
            // A working source must not mask failures at other tracked sources.
            if (sourceFailed) anyUnverified = true
        }

        if (isStopped) return@withContext Result.retry()
        if (MonitorRetryPolicy.shouldRetry(transientFailure, runAttemptCount)) {
            // WorkManager applies exponential backoff. Do not hammer publisher pages.
            Result.retry()
        } else if (!periodic && anyUnverified) {
            // Let the manual Check UI explain that it did not verify all requested pages.
            Result.failure()
        } else {
            // Periodic jobs must NOT fail permanently: next interval tries again,
            // while each source retains its last verified time and its latest error.
            Result.success()
        }
    }
}

object MonitorScheduler {
    private const val WORK_NAME = "journal_status_checks"

    fun schedule(context: Context) {
        val minutes = SubmissionStore.intervalMinutes(context)
        val constraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<MonitorWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(constraint)
            .setInputData(workDataOf("periodicCheck" to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1L, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    /** Return the queued WorkManager ID so the UI can refresh on completion. */
    fun checkNow(context: Context, id: String? = null, sourceUrl: String? = null): java.util.UUID {
        val constraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val inputs = Data.Builder().putBoolean("periodicCheck", false)
        if (id != null) inputs.putString("onlyId", id)
        if (sourceUrl != null) inputs.putString("onlySourceUrl", sourceUrl)
        val request = OneTimeWorkRequestBuilder<MonitorWorker>()
            .setConstraints(constraint)
            .setInputData(inputs.build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1L, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueue(request)
        return request.id
    }
}

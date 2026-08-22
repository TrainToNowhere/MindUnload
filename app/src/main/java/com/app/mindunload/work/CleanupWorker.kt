package com.app.mindunload.work

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.app.mindunload.PlannerApp
import com.app.mindunload.R
import com.app.mindunload.ai.MissingApiKeyException
import com.app.mindunload.reminders.BriefingScheduler
import java.time.Duration
import org.json.JSONArray
import org.json.JSONObject

/**
 * Weekly cleanup: has the model check the backlog for duplicates, long-completed and
 * outdated entries. The suggestions are stored as JSON in the settings and shown
 * in the Today tab for confirmation (apply/ignore) — nothing is deleted
 * automatically.
 */
class CleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PlannerApp
        val items = app.repository.itemDao.allOnce()
        // With a nearly empty backlog there is nothing to clean up — save the API call.
        if (items.size < 5) return Result.success()

        val suggestions = try {
            app.aiService.suggestCleanup(items)
        } catch (e: MissingApiKeyException) {
            return Result.failure()
        } catch (e: Throwable) {
            return Result.retry()
        }

        val array = JSONArray()
        for (s in suggestions) {
            if (s.description.isBlank()) continue
            array.put(
                JSONObject()
                    .put("action", s.action)
                    .put("itemIds", JSONArray(s.itemIds))
                    .put("description", s.description),
            )
        }
        app.settings.cleanupJson = if (array.length() > 0) array.toString() else null

        if (array.length() > 0) {
            BriefingScheduler.ensureChannel(applicationContext)
            val text = applicationContext.resources.getQuantityString(
                R.plurals.cleanup_notification_text,
                array.length(),
                array.length(),
            )
            val notification =
                NotificationCompat.Builder(applicationContext, BriefingScheduler.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(applicationContext.getString(R.string.cleanup_section))
                    .setContentText(text)
                    .setContentIntent(
                        com.app.mindunload.reminders.appContentIntent(
                            applicationContext
                        )
                    )
                    .setAutoCancel(true)
                    .build()
            applicationContext.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification)
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "weekly-cleanup"
        private const val ONCE_NAME = "cleanup-now"
        private const val NOTIFICATION_ID = 43

        /** Weekly run; KEEP so the rhythm survives app starts. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CleanupWorker>(Duration.ofDays(7))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setInitialDelay(Duration.ofDays(1))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Manual run (settings/Today tab); the work id allows progress feedback. */
        fun runNow(context: Context): java.util.UUID {
            val request = OneTimeWorkRequestBuilder<CleanupWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.REPLACE, request)
            return request.id
        }
    }
}

package com.app.ai.planner.work

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.app.ai.planner.PlannerApp
import com.app.ai.planner.R
import com.app.ai.planner.ai.MissingApiKeyException
import com.app.ai.planner.reminders.BriefingScheduler
import java.time.LocalDate

/** Generates the morning-briefing text (Haiku) and posts it as a notification. */
class BriefingWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PlannerApp
        val repo = app.repository
        val claude = app.claudeService
        val settings = app.settings

        // Roll recurring entries forward first so the briefing sees the new occurrences.
        RecurrenceRoller.roll(applicationContext, repo)

        val today = LocalDate.now()
        // Weather, backlog entries and open lists may all be missing — the briefing then
        // simply comes without them.
        val input = collectBriefingInput(applicationContext, repo, settings)

        val text = try {
            claude.generateBriefing(input)
        } catch (e: MissingApiKeyException) {
            return Result.failure()
        } catch (e: Throwable) {
            return Result.retry()
        }

        settings.briefingDate = today.toString()
        settings.briefingText = text

        BriefingScheduler.ensureChannel(applicationContext)
        val notification =
            NotificationCompat.Builder(applicationContext, BriefingScheduler.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(applicationContext.getString(R.string.notification_briefing_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(com.app.ai.planner.reminders.appContentIntent(applicationContext))
                .setAutoCancel(true)
                .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)

        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "briefing-generation"
        private const val NOTIFICATION_ID = 42

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<BriefingWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

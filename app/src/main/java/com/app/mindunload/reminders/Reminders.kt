package com.app.mindunload.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.app.mindunload.PlannerApp
import com.app.mindunload.R
import com.app.mindunload.data.PlannerItem
import kotlinx.coroutines.runBlocking

/** Tapping a notification opens the app (MainActivity). */
fun appContentIntent(context: Context): PendingIntent {
    val intent = Intent(context, com.app.mindunload.ui.MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

object ReminderScheduler {
    const val CHANNEL_ID = "appointments"
    const val EXTRA_ITEM_ID = "itemId"
    const val EXTRA_TITLE = "title"
    const val EXTRA_OFFSET = "offsetMinutes"

    /**
     * Selectable lead times in minutes. [cancel] walks all of them, not just the configured
     * ones — otherwise alarms from an earlier setting would outlive a change.
     */
    val PRESET_OFFSETS = listOf(1440, 120, 60, 30, 15, 10, 5, 0)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_appointments),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    /**
     * One PendingIntent per (appointment, lead time). They are told apart by the data URI —
     * extras are ignored when the system compares PendingIntents, so a differing request
     * code alone would not be enough.
     */
    private fun pendingIntent(context: Context, item: PlannerItem, offset: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData("mindunload://reminder/${item.id}/$offset".toUri())
            .putExtra(EXTRA_ITEM_ID, item.id)
            .putExtra(EXTRA_TITLE, item.title)
            .putExtra(EXTRA_OFFSET, offset)
        return PendingIntent.getBroadcast(
            context,
            item.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(context: Context, item: PlannerItem) {
        val dueAt = item.dueAt ?: return
        val now = System.currentTimeMillis()
        val offsets = (context.applicationContext as PlannerApp).settings.reminderOffsets
        for (offset in offsets) {
            val triggerAt = dueAt - offset * 60_000L
            // Lead times already in the past are skipped: an appointment entered 10 minutes
            // beforehand should still fire its "5 minutes before" reminder.
            if (triggerAt > now) {
                setExactOrFallback(context, triggerAt, pendingIntent(context, item, offset))
            }
        }
    }

    fun cancel(context: Context, item: PlannerItem) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        for (offset in PRESET_OFFSETS) {
            alarmManager.cancel(pendingIntent(context, item, offset))
        }
    }

    /** Re-applies the configured lead times to every upcoming appointment. */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext as PlannerApp
        app.repository.itemDao.upcomingAppointments(System.currentTimeMillis()).forEach {
            cancel(context, it)
            schedule(context, it)
        }
    }
}

/** "At the start" / "30 minutes before" — used both in the settings and on the notification. */
fun reminderOffsetLabel(context: Context, minutes: Int): String = when {
    minutes == 0 -> context.getString(R.string.reminder_offset_start)
    minutes % 1440 == 0 -> context.resources.getQuantityString(
        R.plurals.reminder_offset_days, minutes / 1440, minutes / 1440,
    )
    minutes % 60 == 0 -> context.resources.getQuantityString(
        R.plurals.reminder_offset_hours, minutes / 60, minutes / 60,
    )
    else -> context.resources.getQuantityString(
        R.plurals.reminder_offset_minutes, minutes, minutes,
    )
}

/**
 * Exact alarm when allowed — otherwise an inexact fallback instead of no reminder at all
 * (previously scheduling aborted silently when `canScheduleExactAlarms()` returned false).
 */
private fun setExactOrFallback(context: Context, triggerAt: Long, operation: PendingIntent) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    if (alarmManager.canScheduleExactAlarms()) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
    } else {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: return
        val itemId = intent.getLongExtra(ReminderScheduler.EXTRA_ITEM_ID, 0)
        val offset = intent.getIntExtra(ReminderScheduler.EXTRA_OFFSET, 0)
        ReminderScheduler.ensureChannel(context)
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notification_appointment_title))
            .setContentText(title)
            .setSubText(reminderOffsetLabel(context, offset))
            .setContentIntent(appContentIntent(context))
            .setAutoCancel(true)
            .build()
        // Same id for every lead time of an appointment: the later reminder replaces the
        // earlier one instead of stacking up four notifications for one appointment.
        context.getSystemService(NotificationManager::class.java)
            .notify(itemId.toInt(), notification)
    }
}

/** Re-registers reminders after a device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as PlannerApp
        val pending = goAsync()
        Thread {
            try {
                runBlocking {
                    app.repository.itemDao.upcomingAppointments(System.currentTimeMillis())
                        .forEach { ReminderScheduler.schedule(context, it) }
                }
            } finally {
                pending.finish()
            }
        }.start()
        BriefingScheduler.scheduleNext(context)
    }
}

/** Daily alarm at the configured briefing time (AlarmManager, like [ReminderScheduler]). */
object BriefingScheduler {
    const val CHANNEL_ID = "briefing"
    private const val REQUEST_CODE = 9001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_briefing),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BriefingReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Schedules the next alarm at the configured time (today if not yet past, otherwise tomorrow). */
    fun scheduleNext(context: Context) {
        val settings = (context.applicationContext as PlannerApp).settings
        val now = java.time.LocalDateTime.now()
        var next = now.withHour(settings.briefingHour).withMinute(settings.briefingMinute)
            .withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        setExactOrFallback(context, triggerAt, pendingIntent(context))
    }
}

class BriefingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        com.app.mindunload.work.BriefingWorker.enqueue(context)
        BriefingScheduler.scheduleNext(context)
    }
}

package com.app.ai.planner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.app.ai.planner.R
import com.app.ai.planner.ui.MainActivity
import com.app.ai.planner.ui.VoiceCaptureActivity

/** Home-screen widget: a big voice-capture button, next to it an entry into the app. */
class CaptureWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_capture)
            views.setOnClickPendingIntent(
                R.id.widget_voice,
                activityIntent(context, 0, Intent(context, VoiceCaptureActivity::class.java)),
            )
            views.setOnClickPendingIntent(
                R.id.widget_open_app,
                activityIntent(context, 1, Intent(context, MainActivity::class.java)),
            )
            manager.updateAppWidget(id, views)
        }
    }

    private fun activityIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

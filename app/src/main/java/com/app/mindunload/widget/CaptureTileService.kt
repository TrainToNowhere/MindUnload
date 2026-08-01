package com.app.mindunload.widget

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService
import com.app.mindunload.ui.VoiceCaptureActivity

/** Quick-settings tile: one tap starts direct voice capture. */
class CaptureTileService : TileService() {
    override fun onClick() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, VoiceCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        startActivityAndCollapse(pendingIntent)
    }
}

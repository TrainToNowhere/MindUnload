package com.app.ai.planner.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.app.ai.planner.PlannerApp
import com.app.ai.planner.ui.theme.AIPlannerTheme
import com.app.ai.planner.work.CaptureWorker
import com.app.ai.planner.work.RecurrenceRoller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Without the granted runtime permission Android silently drops all notifications —
        // appointment reminders and the briefing therefore never arrived.
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Catch up on stranded captures (e.g. after process death or without network) at start.
        CaptureWorker.enqueue(application)
        // Roll past recurring appointments forward to their next occurrence.
        val app = application as PlannerApp
        CoroutineScope(Dispatchers.IO).launch {
            RecurrenceRoller.roll(this@MainActivity, app.repository)
        }
        handleShareIntent(intent)
        setContent {
            AIPlannerTheme {
                PlannerNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** Text shared from other apps goes straight into the capture outbox. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
        val app = application as PlannerApp
        CoroutineScope(Dispatchers.IO).launch {
            app.repository.enqueueCapture(text)
            CaptureWorker.enqueue(app)
        }
    }
}

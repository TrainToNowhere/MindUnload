package com.app.mindunload.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.app.mindunload.PlannerApp
import com.app.mindunload.R
import com.app.mindunload.work.CaptureWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Invisible activity for direct voice capture from the widget/quick-settings tile:
 * immediately starts the system speech recognizer and puts the result into the
 * capture outbox — without opening the app.
 */
class VoiceCaptureActivity : ComponentActivity() {
    private val speech = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.takeIf { result.resultCode == Activity.RESULT_OK }
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            val app = application as PlannerApp
            // Process-wide scope like for the share intent: the write survives finish().
            CoroutineScope(Dispatchers.IO).launch {
                app.repository.enqueueCapture(spoken)
                CaptureWorker.enqueue(app)
            }
            Toast.makeText(this, getString(R.string.voice_capture_saved), Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.chat_input_hint))
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.voice_capture_unavailable), Toast.LENGTH_SHORT)
                .show()
            finish()
        }
    }
}

package com.app.ai.planner

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.ai.planner.ai.Whisper
import com.app.ai.planner.ai.WhisperModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the on-device speech recognition exactly as the chat does — same process, same
 * library, same model. Exists because a failing transcription in the app says nothing
 * about *why*: this test names the cause instead.
 *
 * Transcribes the newest recorded voice message already in the app's storage; without
 * one it only checks that the native library and a model are available.
 *
 * ⚠️ Do NOT run this via `gradlew connectedAndroidTest` on a phone whose app data you
 * care about: that task uninstalls the app afterwards, which wipes the database, the
 * downloaded speech models and the API key. Install the two APKs and drive the test
 * directly instead — that leaves the app untouched:
 *
 *     adb install -r app/build/outputs/apk/debug/app-debug.apk
 *     adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
 *     adb shell am instrument -w -e class com.app.ai.planner.WhisperDeviceTest \
 *         com.app.ai.planner.test/androidx.test.runner.AndroidJUnitRunner
 *
 * The transcription time is logged under the tag `AiWhisper` — on a Galaxy S22 Ultra
 * roughly 7 s for 4 s of speech with the base model. Tens of seconds means the native
 * code was built unoptimized (see app/src/main/cpp/CMakeLists.txt).
 */
@RunWith(AndroidJUnit4::class)
class WhisperDeviceTest {

    private val tag = "WhisperDeviceTest"

    @Test
    fun transcribesRecordedVoiceMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val nativeError = Whisper.nativeError()
        Log.i(tag, "native library: ${nativeError ?: "loaded"}")
        assertNotNull("native library failed to load: $nativeError", nativeError == null)

        Log.i(tag, "system info: ${Whisper.systemInfo()}")

        val installed = Whisper.installedModels(context)
        Log.i(tag, "installed models: $installed")
        WhisperModel.entries.forEach {
            Log.i(
                tag,
                "  ${it.fileName}: expected ${it.sizeBytes}, " +
                        "actual ${Whisper.modelFile(context, it).length()}",
            )
        }
        assertTrue("no speech model installed", installed.isNotEmpty())

        val wav = File(context.filesDir, "attachments")
            .listFiles { f -> f.name.endsWith(".wav") }
            ?.maxByOrNull { it.lastModified() }
        if (wav == null) {
            Log.w(tag, "no recorded voice message present — skipping transcription")
            return
        }
        Log.i(tag, "wav: ${wav.name} (${wav.length()} bytes)")

        val model = installed.first()
        val text = runBlocking { Whisper.transcribe(context, model, wav, language = "de") }
        Log.i(tag, "RESULT with ${model.fileName}: >>>$text<<<")
        assertTrue("transcription came back empty", text.isNotBlank())
    }
}

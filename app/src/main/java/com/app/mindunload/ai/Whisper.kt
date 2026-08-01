package com.app.mindunload.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/** Sample rate the recorder produces and whisper.cpp expects. */
private const val VOICE_SAMPLE_RATE = 16_000

/**
 * Raw JNI surface of the bundled whisper.cpp build (see app/src/main/cpp).
 *
 * Deliberately without a `System.loadLibrary` in the initializer: a failing load throws
 * an [UnsatisfiedLinkError] — an `Error`, not an `Exception`. Inside an object
 * initializer it would surface as an `ExceptionInInitializerError` on first use, slip
 * past every `catch (e: Exception)` and leave the caller hanging. [Whisper.nativeError]
 * loads the library where the failure can actually be reported.
 */
internal object WhisperLib {
    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
    external fun transcribe(
        contextPtr: Long,
        numThreads: Int,
        audioData: FloatArray,
        language: String,
    ): String?

    external fun systemInfo(): String
}

/**
 * Selectable speech model. All of them are the q5_1 quantizations — on a phone they are
 * roughly half the size and noticeably faster than the float variants, at a quality loss
 * that does not show up in dictation.
 */
enum class WhisperModel(val fileName: String, val sizeBytes: Long) {
    TINY("ggml-tiny-q5_1.bin", 32_152_673L),
    BASE("ggml-base-q5_1.bin", 59_707_625L),
    SMALL("ggml-small-q5_1.bin", 190_085_487L);

    val downloadUrl: String
        get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"

    companion object {
        val DEFAULT = BASE

        fun fromName(name: String?): WhisperModel =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * On-device speech-to-text. Replaces the system [android.speech.SpeechRecognizer]: its
 * file-based recognition (`EXTRA_AUDIO_SOURCE`) is optional for recognizer implementations
 * and simply fails on a lot of devices — which is exactly the path a recorded voice
 * message needs. whisper.cpp has no such gap, works offline, and keeps the audio on the
 * device.
 *
 * The model is not shipped in the APK (60-190 MB); it is downloaded once and lives in
 * the app's private storage.
 */
object Whisper {

    private const val TAG = "AiWhisper"

    /** whisper.cpp is not safe for concurrent use of one context. */
    private val mutex = Mutex()

    // runCatching, not try/catch: loading a native library fails with an Error, and
    // that has to be captured here rather than unwinding through the caller.
    private val nativeLoad: Result<Unit> by lazy {
        runCatching { System.loadLibrary("whisper_jni") }
    }

    /** Why on-device speech recognition is unavailable, or null when it works. */
    fun nativeError(): String? = nativeLoad.exceptionOrNull()
        ?.let { it.message ?: it.javaClass.simpleName }

    /** Build details of the native library — useful when reporting a problem. */
    fun systemInfo(): String =
        runCatching { WhisperLib.systemInfo() }.getOrElse { "unavailable" }

    private fun modelDir(context: Context): File =
        File(context.filesDir, "whisper").apply { mkdirs() }

    fun modelFile(context: Context, model: WhisperModel): File =
        File(modelDir(context), model.fileName)

    fun isInstalled(context: Context, model: WhisperModel): Boolean {
        val file = modelFile(context, model)
        // A download aborted halfway leaves a short file behind — that would only fail
        // later, inside the model loader, with a far less obvious error.
        return file.exists() && file.length() == model.sizeBytes
    }

    /** Any model file currently on the device, for the "installed" state in the settings. */
    fun installedModels(context: Context): List<WhisperModel> =
        WhisperModel.entries.filter { isInstalled(context, it) }

    fun deleteModel(context: Context, model: WhisperModel) {
        modelFile(context, model).delete()
    }

    /**
     * Downloads a model. Writes to a temporary file first and only moves it into place
     * once it is complete, so an interrupted download can never look installed.
     * [onProgress] gets 0..1.
     */
    suspend fun downloadModel(
        context: Context,
        model: WhisperModel,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val target = modelFile(context, model)
        val temp = File(target.absolutePath + ".part")
        temp.delete()

        val connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw java.io.IOException("HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: model.sizeBytes
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastReported = 0f
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        val progress = (written.toFloat() / total).coerceIn(0f, 1f)
                        // Only report visible steps — otherwise this floods the UI.
                        if (progress - lastReported >= 0.01f) {
                            lastReported = progress
                            onProgress(progress)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (temp.length() != model.sizeBytes) {
            temp.delete()
            throw java.io.IOException("incomplete download (${temp.length()} bytes)")
        }
        target.delete()
        if (!temp.renameTo(target)) throw java.io.IOException("could not store model")
        onProgress(1f)
    }

    /**
     * Transcribes a recorded voice message. [wav] must be the 16 kHz mono 16-bit WAV the
     * recorder produces — that is exactly whisper's input format, so no resampling is
     * needed anywhere.
     *
     * The model is loaded per call and released afterwards: holding 60-190 MB resident
     * for a feature used a few times a day is the worse trade on a phone.
     */
    suspend fun transcribe(
        context: Context,
        model: WhisperModel,
        wav: File,
        language: String = Locale.getDefault().language.ifBlank { "auto" },
    ): String = withContext(Dispatchers.Default) {
        val modelPath = modelFile(context, model)
        if (!isInstalled(context, model)) throw WhisperModelMissingException(model)

        nativeLoad.getOrElse { throw WhisperUnavailableException(it) }

        val samples = readWavSamples(wav)
        if (samples.isEmpty()) return@withContext ""

        mutex.withLock {
            // Every JNI call is wrapped: a crash in native code arrives here as an Error
            // and must not escape as one, or the caller can never report it.
            val ptr = runCatching { WhisperLib.initContext(modelPath.absolutePath) }
                .getOrElse { throw WhisperUnavailableException(it) }
            if (ptr == 0L) throw java.io.IOException("whisper model could not be loaded")
            try {
                val startedAt = System.currentTimeMillis()
                val text = runCatching {
                    WhisperLib.transcribe(ptr, threadCount(), samples, language)
                }.getOrElse { throw WhisperUnavailableException(it) }
                Log.d(
                    TAG,
                    "transcribed ${samples.size / VOICE_SAMPLE_RATE}s with ${model.fileName} " +
                            "in ${System.currentTimeMillis() - startedAt}ms",
                )
                text.orEmpty().trim()
            } finally {
                runCatching { WhisperLib.freeContext(ptr) }
            }
        }
    }

    /**
     * Whisper scales badly past the big cores, and on a phone the little cores would only
     * drag the whole inference down to their pace.
     */
    private fun threadCount(): Int =
        (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)

    /** 16-bit PCM WAV → float samples in [-1, 1], which is what whisper.cpp expects. */
    private fun readWavSamples(wav: File): FloatArray {
        val bytes = wav.readBytes()
        if (bytes.size <= 44) return FloatArray(0)
        val buffer = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        val samples = FloatArray((bytes.size - 44) / 2)
        for (i in samples.indices) samples[i] = buffer.short / 32_768f
        return samples
    }
}

/** The selected speech model is not on the device (yet). */
class WhisperModelMissingException(val model: WhisperModel) :
    Exception("whisper model ${model.fileName} not installed")

/**
 * The native library could not be loaded or crashed. Wraps the original [Throwable] —
 * usually an `Error` — so the failure can travel through normal exception handling.
 */
class WhisperUnavailableException(cause: Throwable) :
    Exception(cause.message ?: cause.javaClass.simpleName, cause)

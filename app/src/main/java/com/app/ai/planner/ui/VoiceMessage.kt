package com.app.ai.planner.ui

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MediaPlayer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * Voice messages work like in a messenger: record first, keep the recording, transcribe
 * afterwards. The audio is therefore captured raw via [AudioRecord] — the recorder writes
 * a WAV the chat can play back, and the very same samples go to whisper.cpp afterwards
 * (see [com.app.ai.planner.ai.Whisper]).
 *
 * 16 kHz mono is not a compromise here, it is exactly whisper's input format — nothing
 * has to be resampled anywhere.
 */
const val VOICE_SAMPLE_RATE = 16_000

/** WAV header length; the PCM payload starts behind it. */
private const val WAV_HEADER_BYTES = 44

/** Records mono 16-bit PCM into a WAV file. */
class VoiceRecorder(private val context: Context) {

    private var record: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    /** Current input level 0..1 — drives the level bars while recording. */
    @Volatile
    var amplitude: Float = 0f
        private set

    /** Recorded samples so far, as milliseconds. */
    @Volatile
    var durationMs: Long = 0L
        private set

    private var target: File? = null

    @SuppressLint("MissingPermission") // the caller holds RECORD_AUDIO
    fun start(file: File): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(
            VOICE_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false
        val bufferSize = minBuffer * 2
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                VOICE_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        }.getOrNull() ?: return false
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }

        target = file
        durationMs = 0L
        amplitude = 0f
        record = recorder
        running = true
        recorder.startRecording()

        worker = thread(name = "voice-recorder") {
            val out = file.outputStream().buffered()
            // Placeholder header — the sizes are only known once recording stops.
            out.write(ByteArray(WAV_HEADER_BYTES))
            val buffer = ByteArray(bufferSize)
            var totalBytes = 0L
            while (running) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                out.write(buffer, 0, read)
                totalBytes += read
                amplitude = peakOf(buffer, read)
                durationMs = totalBytes / 2 * 1000 / VOICE_SAMPLE_RATE
            }
            out.flush()
            out.close()
            writeWavHeader(file, totalBytes)
        }
        return true
    }

    /** Stops and finalizes the WAV; returns the file, or null when nothing was recorded. */
    fun stop(): File? {
        if (!running) return null
        running = false
        runCatching { record?.stop() }
        record?.release()
        record = null
        worker?.join(2_000)
        worker = null
        amplitude = 0f
        val file = target
        target = null
        return file?.takeIf { it.exists() && it.length() > WAV_HEADER_BYTES }
    }

    /** Aborts without keeping the recording. */
    fun cancel() {
        val file = stop()
        file?.delete()
    }

    private fun peakOf(buffer: ByteArray, length: Int): Float {
        var peak = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))
            val magnitude = kotlin.math.abs(sample)
            if (magnitude > peak) peak = magnitude
            i += 2
        }
        return (peak / 32_768f).coerceIn(0f, 1f)
    }

    private fun writeWavHeader(file: File, dataBytes: Long) {
        val header = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val byteRate = VOICE_SAMPLE_RATE * 2
        header.put("RIFF".toByteArray())
        header.putInt((36 + dataBytes).toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)          // PCM header size
        header.putShort(1)         // format: PCM
        header.putShort(1)         // channels: mono
        header.putInt(VOICE_SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort(2)         // block align
        header.putShort(16)        // bits per sample
        header.put("data".toByteArray())
        header.putInt(dataBytes.toInt())
        runCatching {
            RandomAccessFile(file, "rw").use {
                it.seek(0)
                it.write(header.array())
            }
        }
    }
}

/**
 * Playback of a single voice message. Only one plays at a time — the chat hands the same
 * instance around so starting a second bubble stops the first.
 */
class VoicePlayer {
    private var player: MediaPlayer? = null
    private var playingPath: String? = null

    fun isPlaying(path: String): Boolean = playingPath == path

    fun toggle(path: String, onFinished: () -> Unit) {
        if (playingPath == path) {
            stop()
            onFinished()
            return
        }
        stop()
        val media = MediaPlayer()
        val started = runCatching {
            media.setDataSource(path)
            media.prepare()
            media.setOnCompletionListener {
                stop()
                onFinished()
            }
            media.start()
        }.isSuccess
        if (!started) {
            media.release()
            onFinished()
            return
        }
        player = media
        playingPath = path
    }

    fun stop() {
        runCatching { player?.stop() }
        player?.release()
        player = null
        playingPath = null
    }
}

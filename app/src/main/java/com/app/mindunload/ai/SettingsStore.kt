package com.app.mindunload.ai

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsStore(context: Context) {
    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** OpenRouter API key (https://openrouter.ai/keys) — all AI calls go through OpenRouter. */
    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit { putString(KEY_API_KEY, value?.trim()) }
        }

    /** OpenRouter model id used for structuring/parsing/quick answers, e.g. "anthropic/claude-haiku-4.5". */
    var fastModel: String
        get() = prefs.getString(KEY_FAST_MODEL, OpenRouterModels.DEFAULT_FAST) ?: OpenRouterModels.DEFAULT_FAST
        set(value) {
            prefs.edit { putString(KEY_FAST_MODEL, value) }
        }

    /** OpenRouter model id used for research/review, e.g. "anthropic/claude-sonnet-5". */
    var strongModel: String
        get() = prefs.getString(KEY_STRONG_MODEL, OpenRouterModels.DEFAULT_STRONG)
            ?: OpenRouterModels.DEFAULT_STRONG
        set(value) {
            prefs.edit { putString(KEY_STRONG_MODEL, value) }
        }

    /** ISO date (yyyy-MM-dd) for which [briefingText] was last generated. */
    var briefingDate: String?
        get() = prefs.getString(KEY_BRIEFING_DATE, null)
        set(value) {
            prefs.edit { putString(KEY_BRIEFING_DATE, value) }
        }

    var briefingText: String?
        get() = prefs.getString(KEY_BRIEFING_TEXT, null)
        set(value) {
            prefs.edit { putString(KEY_BRIEFING_TEXT, value) }
        }

    /** Time of day at which the morning briefing is generated and posted. */
    var briefingHour: Int
        get() = prefs.getInt(KEY_BRIEFING_HOUR, 7)
        set(value) {
            prefs.edit { putInt(KEY_BRIEFING_HOUR, value) }
        }

    var briefingMinute: Int
        get() = prefs.getInt(KEY_BRIEFING_MINUTE, 0)
        set(value) {
            prefs.edit { putInt(KEY_BRIEFING_MINUTE, value) }
        }

    /**
     * Lead times for appointment reminders, in minutes before the appointment; 0 = at the start.
     * Several fire in sequence (e.g. 30, 15, 5, 0). Empty = no reminders at all.
     * Default is the behaviour that was hard-coded before: a single alarm at the start.
     */
    var reminderOffsets: List<Int>
        get() {
            val raw = prefs.getString(KEY_REMINDER_OFFSETS, null) ?: return listOf(0)
            return raw.split(',').mapNotNull { it.trim().toIntOrNull() }
                .distinct().sortedDescending()
        }
        set(value) {
            prefs.edit {
                putString(
                    KEY_REMINDER_OFFSETS,
                    value.distinct().sortedDescending().joinToString(","),
                )
            }
        }

    /**
     * Speech model used for voice messages (see [WhisperModel]). Only the name is stored;
     * whether the file is actually on the device is answered by [Whisper.isInstalled].
     */
    var whisperModel: WhisperModel
        get() = WhisperModel.fromName(prefs.getString(KEY_WHISPER_MODEL, null))
        set(value) {
            prefs.edit { putString(KEY_WHISPER_MODEL, value.name) }
        }

    /** Pending weekly-cleanup suggestions as a JSON array (written by the CleanupWorker). */
    var cleanupJson: String?
        get() = prefs.getString(KEY_CLEANUP_JSON, null)
        set(value) {
            prefs.edit { putString(KEY_CLEANUP_JSON, value) }
        }

    /** Location for the briefing weather (Open-Meteo). Null = no weather in the briefing. */
    var weatherLatitude: Double?
        get() = prefs.getString(KEY_WEATHER_LAT, null)?.toDoubleOrNull()
        set(value) {
            prefs.edit { putString(KEY_WEATHER_LAT, value?.toString()) }
        }

    var weatherLongitude: Double?
        get() = prefs.getString(KEY_WEATHER_LON, null)?.toDoubleOrNull()
        set(value) {
            prefs.edit { putString(KEY_WEATHER_LON, value?.toString()) }
        }

    /** Monthly API budget in USD for the usage dashboard. */
    var monthlyBudgetUsd: Float
        get() = prefs.getFloat(KEY_MONTHLY_BUDGET, 5f)
        set(value) {
            prefs.edit { putFloat(KEY_MONTHLY_BUDGET, value) }
        }

    private companion object {
        const val KEY_CLEANUP_JSON = "cleanup_json"
        const val KEY_API_KEY = "openrouter_api_key"
        const val KEY_FAST_MODEL = "openrouter_fast_model"
        const val KEY_STRONG_MODEL = "openrouter_strong_model"
        const val KEY_BRIEFING_DATE = "briefing_date"
        const val KEY_BRIEFING_TEXT = "briefing_text"
        const val KEY_BRIEFING_HOUR = "briefing_hour"
        const val KEY_BRIEFING_MINUTE = "briefing_minute"
        const val KEY_WEATHER_LAT = "weather_lat"
        const val KEY_WEATHER_LON = "weather_lon"
        const val KEY_MONTHLY_BUDGET = "monthly_budget_usd"
        const val KEY_WHISPER_MODEL = "whisper_model"
        const val KEY_REMINDER_OFFSETS = "reminder_offsets"
    }
}

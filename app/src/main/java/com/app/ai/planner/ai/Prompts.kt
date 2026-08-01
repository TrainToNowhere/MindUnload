package com.app.ai.planner.ai

import android.content.Context
import androidx.annotation.RawRes
import java.util.Locale

/**
 * Loads the AI prompts from res/raw. They live there (one plain-text file per prompt,
 * English only) instead of strings.xml because string resources collapse newlines and
 * indentation, which would mangle the prompt structure.
 */
class Prompts(context: Context) {
    private val appContext = context.applicationContext
    private val cache = mutableMapOf<Int, String>()

    fun raw(@RawRes id: Int): String = synchronized(cache) {
        cache.getOrPut(id) {
            appContext.resources.openRawResource(id).bufferedReader().use { it.readText() }.trim()
        }
    }

    /** Prompt text plus the rule that answers must be in the device language. */
    fun withLanguageRule(@RawRes id: Int): String = raw(id) + "\n\n" + languageRule()

    /**
     * Appended to every prompt: the prompts themselves are English, but all user-facing
     * output must follow the device language (German phone → German answers).
     */
    fun languageRule(): String {
        val language = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)
            .ifBlank { "English" }
        return "IMPORTANT: The user's device language is $language. All user-facing text " +
                "you produce — answers, summaries, plans, confirmations, descriptions, " +
                "reasons — MUST be written in $language, unless the user explicitly asks " +
                "for another language."
    }
}

package com.app.mindunload.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One OpenRouter model available for selection — see [OpenRouterModels.fetchCatalog]. */
data class OpenRouterModel(val id: String, val label: String, val provider: String)

/**
 * Loads the currently available OpenRouter models from https://openrouter.ai/api/v1/models
 * (a public endpoint, no API key needed) instead of a hand-maintained list — providers
 * rename, replace and retire models often enough that a hardcoded list goes stale within
 * weeks. The result is filtered down to a handful of well-known providers and to models
 * that support both tool calling and structured JSON output, since the parsing/chat
 * pipeline (see [AiService]) relies on both; within each provider only the newest few are
 * kept so the picker in Settings stays short instead of listing all ~300 models.
 */
object OpenRouterModels {
    // Fallback defaults for a fresh install, before the user has picked anything (or before
    // the live catalog has loaded) — narrow on purpose, just enough to make the app usable.
    const val DEFAULT_FAST = "anthropic/claude-haiku-4.5"
    const val DEFAULT_STRONG = "anthropic/claude-sonnet-5"

    private val ALLOWED_PREFIXES = listOf(
        "anthropic/", "openai/", "google/", "x-ai/", "deepseek/", "meta/", "meta-llama/",
        "qwen/", "moonshotai/", "minimax/",
    )
    private const val MODELS_PER_PROVIDER = 4

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchCatalog(): List<OpenRouterModel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://openrouter.ai/api/v1/models").build()
        val bodyText = http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw AiServiceException(resp.code, resp.message)
            resp.body?.string().orEmpty()
        }
        val entries = JSONObject(bodyText).getJSONArray("data")

        data class Candidate(val created: Long, val model: OpenRouterModel)

        (0 until entries.length()).asSequence()
            .map { entries.getJSONObject(it) }
            .filter { m -> ALLOWED_PREFIXES.any { prefix -> m.getString("id").startsWith(prefix) } }
            // ":batch"/":free" suffixes are variants of the same model, not distinct choices.
            .filter { m -> ':' !in m.getString("id") }
            .filter { m ->
                val params = m.optJSONArray("supported_parameters")
                val supported = params?.let { arr -> (0 until arr.length()).map(arr::getString) }.orEmpty()
                "tools" in supported && ("response_format" in supported || "structured_outputs" in supported)
            }
            .map { m ->
                val id = m.getString("id")
                val name = m.optString("name", id)
                val provider = name.substringBefore(": ", missingDelimiterValue = id.substringBefore("/"))
                val label = name.substringAfter(": ", missingDelimiterValue = name)
                Candidate(m.optLong("created", 0L), OpenRouterModel(id, label, provider))
            }
            .groupBy { it.model.provider }
            .values
            .flatMap { group -> group.sortedByDescending { it.created }.take(MODELS_PER_PROVIDER) }
            .map { it.model }
            .sortedWith(compareBy({ it.provider }, { it.label }))
            .toList()
    }

    fun labelFor(id: String, catalog: List<OpenRouterModel>): String =
        catalog.find { it.id == id }?.let { "${it.provider} · ${it.label}" } ?: id
}

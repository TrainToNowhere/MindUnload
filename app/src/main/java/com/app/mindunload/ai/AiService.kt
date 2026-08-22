package com.app.mindunload.ai

import android.util.Log
import com.app.mindunload.R
import com.app.mindunload.data.ItemType
import com.app.mindunload.data.LinkRelation
import com.app.mindunload.data.PlannerItem
import com.app.mindunload.data.Priority
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

class MissingApiKeyException : Exception("API key not configured")

/** A non-2xx response from OpenRouter. [statusCode] drives retry decisions in the workers. */
class AiServiceException(val statusCode: Int, message: String) : Exception(message)

/**
 * Sink for the usage dashboard: PlannerApp attaches a recorder here that writes every
 * API call into the api_usage table. Global instead of injected so that
 * ResearchService/WikiService log as well without refactoring.
 */
object AiUsageLog {
    @Volatile
    var recorder: ((feature: String, model: String, input: Long, cacheWrite: Long, cacheRead: Long, output: Long) -> Unit)? =
        null
}

/**
 * Targeted backlog access for the AI tools: instead of dumping the whole backlog into
 * the prompt (doesn't scale), the model searches on demand — implemented by the
 * CaptureWorker on top of the DAO queries.
 */
interface BacklogTools {
    suspend fun search(query: String, type: ItemType?): List<PlannerItem>
    suspend fun recent(type: ItemType?): List<PlannerItem>

    /** All open entries, optionally of one type — "welche Aufgaben habe ich noch?". */
    suspend fun open(type: ItemType?): List<PlannerItem>

    /** Dated, open entries whose dueAt falls into [fromMillis, toMillis]. */
    suspend fun agenda(fromMillis: Long, toMillis: Long): List<PlannerItem>
}

/** One earlier exchange in a multi-turn chat mode — see [AiService.structureThoughts]. */
data class ThoughtTurn(val userText: String, val assistantText: String)

/**
 * Talks to whichever provider/model the user picked in Settings via OpenRouter's
 * OpenAI-compatible chat-completions endpoint (https://openrouter.ai/docs) — one API key,
 * any model. [SettingsStore.fastModel] is used for structuring/quick answers,
 * [SettingsStore.strongModel] for research/review.
 */
class AiService(
    private val settings: SettingsStore,
    private val prompts: Prompts,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Structures a free-form input into planner items + link suggestions.
     * Instead of a backlog dump in the prompt, the model looks up entries itself via
     * [tools] — only when the input contains commands or link candidates.
     */
    suspend fun parseCommand(
        rawText: String,
        tools: BacklogTools,
        listNames: List<String> = emptyList(),
        categories: List<String> = emptyList(),
        now: LocalDateTime = LocalDateTime.now(),
    ): ParsedCommand = withContext(Dispatchers.IO) {
        val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val userMessage = """
            Current date/time: $weekday, ${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}

            Existing shopping list names: ${
            listNames.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none"
        }
            Existing categories: ${
            categories.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none"
        }

            User input to parse:
            $rawText
        """.trimIndent()

        val response = runToolLoop(
            model = settings.fastModel,
            system = prompts.withLanguageRule(R.raw.prompt_parse_system),
            userText = userMessage,
            tools = listOf(SEARCH_ITEMS_TOOL, LIST_RECENT_TOOL),
            backlogTools = tools,
            feature = "parseCommand",
            responseFormat = jsonSchemaFormat("parsed_command", PARSED_COMMAND_SCHEMA_JSON),
        )
        val json = textOf(response)
            .ifBlank { throw IllegalStateException("Empty response from model") }
        MAPPER.readValue(json, ParsedCommand::class.java)
    }

    /**
     * Chat mode "Ask": answers queries/summaries over the user's own data —
     * tasks on a topic, appointments, goals, knowledge, day planning. Gets the upcoming
     * appointments/due tasks as context and looks up everything else itself via tools.
     */
    suspend fun answerQuery(
        question: String,
        tools: BacklogTools,
        upcomingAppointments: List<PlannerItem>,
        openTasks: List<PlannerItem>,
        openCounts: Map<ItemType, Int> = emptyMap(),
        now: LocalDateTime = LocalDateTime.now(),
    ): String = withContext(Dispatchers.IO) {
        fun line(item: PlannerItem): String {
            val due = item.dueAt?.let {
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEE dd.MM. HH:mm", Locale.ENGLISH))
            }
            return "- [${item.type.name.lowercase()}] ${item.title}" +
                    (due?.let { " ($it)" } ?: "") +
                    (if (item.priority != Priority.NONE) " [prio: ${item.priority.name.lowercase()}]" else "")
        }

        val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val userMessage = """
            Current date/time: $weekday, ${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}

            The user's open entries by type (fetch them with list_open):
            ${
            ItemType.entries.joinToString("\n") { type ->
                "- ${type.name.lowercase()}: ${openCounts[type] ?: 0}"
            }
        }

            Upcoming appointments (next 14 days):
            ${
            upcomingAppointments.takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { line(it) } ?: "none"
        }

            Open tasks with a due date:
            ${openTasks.takeIf { it.isNotEmpty() }?.joinToString("\n") { line(it) } ?: "none"}

            The user's question/request:
            $question
        """.trimIndent()

        val response = runToolLoop(
            model = settings.fastModel,
            system = prompts.withLanguageRule(R.raw.prompt_ask_system),
            userText = userMessage,
            tools = listOf(SEARCH_ITEMS_TOOL, LIST_RECENT_TOOL, LIST_OPEN_TOOL, LIST_AGENDA_TOOL),
            backlogTools = tools,
            feature = "askChat",
            maxTokens = 2048L,
        )
        textOf(response).ifBlank { throw IllegalStateException("Empty answer") }
    }

    /**
     * Chat mode "Structure thoughts": turns a (typically long, transcribed) stream of
     * thought into a structured Markdown note with an action-item checklist — and,
     * because it is a real conversation, can ask a follow-up question instead of
     * guessing, or revise the note once the user replies. [history] is the earlier
     * turns of this same conversation, oldest first. Runs on the fast model — this is
     * reformatting and light dialogue, not research or generation.
     */
    suspend fun structureThoughts(
        text: String,
        history: List<ThoughtTurn> = emptyList(),
    ): String = withContext(Dispatchers.IO) {
        val messages = mutableListOf<JSONObject>()
        history.forEach { turn ->
            messages += userMessage(turn.userText)
            messages += JSONObject().put("role", "assistant").put("content", turn.assistantText)
        }
        messages += userMessage(text)
        val response = postChat(
            model = settings.fastModel,
            system = prompts.withLanguageRule(R.raw.prompt_structure_system),
            messages = messages,
            maxTokens = 4096L,
        )
        logUsage("structureThoughts", response)
        textOf(response).ifBlank { throw IllegalStateException("Empty structured note") }
    }

    /**
     * Weekly cleanup: checks the entire backlog for duplicates, long-completed and
     * obviously outdated entries and returns conservative suggestions. Runs only
     * once a week — the full dump is deliberately fine here.
     */
    suspend fun suggestCleanup(
        items: List<PlannerItem>,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<CleanupSuggestion> = withContext(Dispatchers.IO) {
        val dayMs = 86_400_000L
        val nowMs = System.currentTimeMillis()
        val backlog = items.joinToString("\n") { item ->
            "- id=${item.id} type=${item.type.name.lowercase()} title=\"${item.title}\"" +
                    (item.listName?.let { " list=\"$it\"" } ?: "") +
                    (item.category?.let { " category=\"$it\"" } ?: "") +
                    (item.dueAt?.let { " dueInDays=${(it - nowMs) / dayMs}" } ?: "") +
                    " ageDays=${(nowMs - item.createdAt) / dayMs}" +
                    (if (item.done) " done=true" else "")
        }
        val prompt = """
            Current date/time: ${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}

            Full backlog:
            $backlog

            ${prompts.withLanguageRule(R.raw.prompt_cleanup)}
        """.trimIndent()

        val response = postChat(
            model = settings.fastModel,
            system = null,
            messages = listOf(userMessage(prompt)),
            responseFormat = jsonSchemaFormat("cleanup_result", CLEANUP_SCHEMA_JSON),
            maxTokens = 2048L,
        )
        logUsage("suggestCleanup", response)
        val json = textOf(response)
            .ifBlank { throw IllegalStateException("Empty response from model") }
        MAPPER.readValue(json, CleanupResult::class.java).suggestions
    }

    /**
     * Review: reflective report over a period (week/month/year) from completed,
     * newly captured, occurred and archived entries.
     * Runs on the strong model — reflection quality matters here.
     */
    suspend fun generateReview(
        items: List<PlannerItem>,
        from: Long,
        to: Long,
        periodLabel: String,
    ): String = withContext(Dispatchers.IO) {
        fun fmt(ms: Long): String = LocalDateTime
            .ofInstant(java.time.Instant.ofEpochMilli(ms), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd.MM."))

        fun describe(item: PlannerItem): String = buildString {
            append("- [${item.type.name.lowercase()}] ${item.title}")
            item.category?.let { append(" (category: $it)") }
            item.notes?.takeIf { item.type == ItemType.NOTE }?.let { append(" — ${it.take(140)}") }
        }

        val done = items.filter { it.doneAt != null && it.doneAt in from..to }
        val happened =
            items.filter { it.type == ItemType.APPOINTMENT && it.dueAt != null && it.dueAt in from..to }
        val learned = items.filter { it.type == ItemType.NOTE && it.createdAt in from..to }
        val created = items.filter {
            it.createdAt in from..to && it !in done && it !in happened && it !in learned
        }
        val prompt = """
            Period: $periodLabel (${fmt(from)} to ${fmt(to)})

            Completed in this period:
            ${
            done.takeIf { it.isNotEmpty() }?.joinToString("\n") { describe(it) } ?: "none recorded"
        }

            Appointments/events in this period:
            ${happened.takeIf { it.isNotEmpty() }?.joinToString("\n") { describe(it) } ?: "none"}

            Newly learned knowledge (notes):
            ${learned.takeIf { it.isNotEmpty() }?.joinToString("\n") { describe(it) } ?: "none"}

            Newly captured (tasks/ideas/goals/shopping):
            ${created.takeIf { it.isNotEmpty() }?.joinToString("\n") { describe(it) } ?: "none"}

            ${prompts.withLanguageRule(R.raw.prompt_review)}
        """.trimIndent()

        val response = postChat(
            model = settings.strongModel,
            system = null,
            messages = listOf(userMessage(prompt)),
            maxTokens = 2048L,
        )
        logUsage("generateReview", response)
        textOf(response).ifBlank { throw IllegalStateException("Empty review") }
    }

    /**
     * Image-to-text: reads out everything legible on a photo (receipt, note, poster,
     * screenshot) so the normal chat pipeline can work on plain text afterwards.
     * Runs on the fast model — OCR needs no reasoning depth.
     *
     * [imageBase64] is the JPEG stored by [com.app.mindunload.data.Attachments], already
     * scaled down to a useful maximum edge.
     */
    suspend fun extractTextFromImage(
        imageBase64: String,
        mediaType: String = "image/jpeg",
    ): String = withContext(Dispatchers.IO) {
        val content = JSONArray()
            .put(
                JSONObject().put("type", "text")
                    .put("text", prompts.withLanguageRule(R.raw.prompt_image_text)),
            )
            .put(
                JSONObject().put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject().put("url", "data:$mediaType;base64,$imageBase64"),
                    ),
            )
        val response = postChat(
            model = settings.fastModel,
            system = null,
            messages = listOf(JSONObject().put("role", "user").put("content", content)),
            maxTokens = 2048L,
        )
        logUsage("extractTextFromImage", response)
        textOf(response).trim()
    }

    /**
     * Writes the morning-briefing text (free text, no structured output) from today's
     * appointments, due/overdue tasks, weather, backlog suggestions and shopping lists.
     */
    suspend fun generateBriefing(
        input: com.app.mindunload.work.BriefingInput,
        now: LocalDateTime = LocalDateTime.now(),
    ): String = withContext(Dispatchers.IO) {
        val appointmentsToday = input.appointmentsToday
        val dueSoonTasks = input.dueSoonTasks
        val overdueTasks = input.overdueTasks
        val weatherSummary = input.weather
        val backlogSuggestions = input.backlogSuggestions
        val shoppingLists = input.shoppingLists

        fun describe(item: PlannerItem, withTime: Boolean): String {
            val time = item.dueAt?.takeIf { withTime }
                ?.let {
                    LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(it),
                        ZoneId.systemDefault()
                    )
                }
                ?.format(DateTimeFormatter.ofPattern("HH:mm"))
            return buildString {
                if (time != null) append("$time: ")
                append(item.title)
            }
        }

        val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val prompt = """
            Today's date: $weekday, ${now.format(DateTimeFormatter.ISO_LOCAL_DATE)}

            Today's appointments:
            ${
            appointmentsToday.takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { "- " + describe(it, true) } ?: "none"
        }

            Tasks due soon:
            ${
            dueSoonTasks.takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { "- " + describe(it, false) } ?: "none"
        }

            Overdue tasks:
            ${
            overdueTasks.takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { "- " + describe(it, false) } ?: "none"
        }

            Weather today:
            ${weatherSummary ?: "no weather data available"}

            Backlog entries lying around for a while (undated tasks/ideas/goals):
            ${
            backlogSuggestions.takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { "- [${it.type.name.lowercase()}] ${it.title}" } ?: "none"
        }

            Other open tasks without a date:
            ${
            input.openTasks.takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { "- " + describe(it, false) } ?: "none"
        }

            Open goals:
            ${
            input.goals.takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { "- " + describe(it, false) } ?: "none"
        }

            Open ideas:
            ${
            input.ideas.takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { "- " + describe(it, false) } ?: "none"
        }

            Shopping lists (open items):
            ${shoppingLists.takeIf { it.isNotEmpty() }?.entries?.joinToString("\n") { "- ${it.key}: ${it.value} items" } ?: "none"}

            ${prompts.withLanguageRule(R.raw.prompt_briefing)}
        """.trimIndent()

        val response = postChat(
            model = settings.fastModel,
            system = null,
            messages = listOf(userMessage(prompt)),
            maxTokens = 768L,
        )
        logUsage("generateBriefing", response)
        textOf(response).ifBlank { throw IllegalStateException("Empty briefing") }
    }

    /** Generic single-turn free-text answer on the fast model — used by [WikiService]. */
    suspend fun quickAnswer(prompt: String, maxTokens: Long = 512L): String =
        withContext(Dispatchers.IO) {
            val response = postChat(
                model = settings.fastModel,
                system = null,
                messages = listOf(userMessage(prompt)),
                maxTokens = maxTokens,
            )
            logUsage("wiki", response)
            textOf(response).ifBlank { throw IllegalStateException("Empty answer") }
        }

    /**
     * Web-search-backed answer on the strong model, using OpenRouter's built-in web plugin
     * (the ":online" model suffix) instead of a provider-specific search tool — works the
     * same regardless of which underlying provider/model is selected. Used by [ResearchService].
     */
    suspend fun researchAnswer(prompt: String, feature: String): String =
        withContext(Dispatchers.IO) {
            val response = postChat(
                model = "${settings.strongModel}:online",
                system = null,
                messages = listOf(userMessage(prompt)),
                maxTokens = 4096L,
            )
            logUsage(feature, response)
            textOf(response).ifBlank { throw IllegalStateException("Empty research result") }
        }

    // ---- OpenRouter HTTP plumbing ----

    private fun userMessage(text: String): JSONObject =
        JSONObject().put("role", "user").put("content", text)

    private fun jsonSchemaFormat(name: String, schemaJson: String): JSONObject =
        JSONObject()
            .put("type", "json_schema")
            .put(
                "json_schema",
                JSONObject()
                    .put("name", name)
                    .put("strict", true)
                    .put("schema", JSONObject(schemaJson)),
            )

    private fun textOf(response: JSONObject): String {
        val choices = response.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""
        val message = choices.getJSONObject(0).optJSONObject("message") ?: return ""
        return message.optString("content", "")
    }

    /**
     * Tool loop: answer the model's search requests against the DB until the final
     * response arrives. Round limit as a cost brake.
     */
    private suspend fun runToolLoop(
        model: String,
        system: String,
        userText: String,
        tools: List<JSONObject>,
        backlogTools: BacklogTools,
        feature: String,
        responseFormat: JSONObject? = null,
        maxTokens: Long = 4096L,
    ): JSONObject {
        val messages = mutableListOf(userMessage(userText))
        var response = postChat(model, system, messages, tools, responseFormat, maxTokens)
        logUsage(feature, response)
        var rounds = 0
        var choice = response.getJSONArray("choices").getJSONObject(0)
        while (choice.optString("finish_reason") == "tool_calls" && rounds < 6) {
            val message = choice.getJSONObject("message")
            messages += message
            val toolCalls = message.optJSONArray("tool_calls") ?: JSONArray()
            if (toolCalls.length() == 0) break
            for (i in 0 until toolCalls.length()) {
                val call = toolCalls.getJSONObject(i)
                val fn = call.getJSONObject("function")
                val result = runBacklogTool(backlogTools, fn.getString("name"), fn.optString("arguments"))
                messages += JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", call.getString("id"))
                    .put("content", result)
            }
            response = postChat(model, system, messages, tools, responseFormat, maxTokens)
            logUsage("$feature/round${rounds + 1}", response)
            choice = response.getJSONArray("choices").getJSONObject(0)
            rounds++
        }
        return response
    }

    private fun postChat(
        model: String,
        system: String?,
        messages: List<JSONObject>,
        tools: List<JSONObject> = emptyList(),
        responseFormat: JSONObject? = null,
        maxTokens: Long = 2048L,
    ): JSONObject {
        val apiKey = settings.apiKey ?: throw MissingApiKeyException()
        val allMessages = JSONArray()
        system?.let { allMessages.put(JSONObject().put("role", "system").put("content", it)) }
        messages.forEach { allMessages.put(it) }

        val body = JSONObject()
            .put("model", model)
            .put("messages", allMessages)
            .put("max_tokens", maxTokens)
            .put("usage", JSONObject().put("include", true))
        if (tools.isNotEmpty()) body.put("tools", JSONArray(tools))
        responseFormat?.let { body.put("response_format", it) }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://github.com/TrainToNowhere/MindUnload")
            .addHeader("X-Title", "MindUnload")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val bodyText = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw AiServiceException(resp.code, bodyText.take(500).ifBlank { resp.message })
            }
            return JSONObject(bodyText)
        }
    }

    /** Executes a search_items/list_recent call from the model and formats the result compactly. */
    private suspend fun runBacklogTool(tools: BacklogTools, name: String, argumentsJson: String): String {
        val input = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }
            .getOrDefault(JSONObject())
        fun stringOrNull(key: String): String? = input.optString(key, "").takeIf { it.isNotEmpty() }
        val type = toItemTypeOrNull(stringOrNull("type"))
        val items = when (name) {
            "search_items" -> {
                val query = input.optString("query", "").trim()
                if (query.isEmpty()) return "error: missing query"
                tools.search(query, type)
            }

            "list_recent" -> tools.recent(type)

            "list_open" -> tools.open(type)

            "list_agenda" -> {
                val from = parseToolDate(stringOrNull("from"), endOfDay = false)
                    ?: return "error: invalid or missing 'from' (expected YYYY-MM-DD)"
                val to = parseToolDate(stringOrNull("to"), endOfDay = true)
                    ?: return "error: invalid or missing 'to' (expected YYYY-MM-DD)"
                if (to < from) return "error: 'to' lies before 'from'"
                tools.agenda(from, to)
            }

            else -> return "error: unknown tool"
        }
        if (items.isEmpty()) return "no matches"
        return items.joinToString("\n") { item ->
            "- id=${item.id} type=${item.type.name.lowercase()} title=\"${item.title}\"" +
                    // The body, for knowledge entries only: without it the model can neither
                    // answer knowledge questions nor rewrite an entry. JSON-quoted so that
                    // line breaks in the text don't break the one-entry-per-line format.
                    (item.notes?.takeIf { item.type == ItemType.NOTE }
                        ?.let { " notes=" + JSONObject.quote(it.take(1000)) } ?: "") +
                    (item.category?.let { " category=\"$it\"" } ?: "") +
                    (item.tags.takeIf { it.isNotEmpty() }?.let { " tags=${it.joinToString(",")}" }
                        ?: "") +
                    (item.listName?.let { " list=\"$it\"" } ?: "") +
                    (item.dueAt?.let {
                        // Weekday included: the model reliably miscalculates it from the date alone.
                        val due = LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(it),
                            ZoneId.systemDefault(),
                        )
                        " due=" + due.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) +
                                " (" + due.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + ")"
                    } ?: "") +
                    (item.recurrence?.let { " recurrence=$it" } ?: "") +
                    (if (item.done) " done=true" else "")
        }
    }

    /** "YYYY-MM-DD" → epoch millis at start (or, for range ends, end) of that local day. */
    private fun parseToolDate(value: String?, endOfDay: Boolean): Long? {
        val date = value?.trim()?.take(10)?.let {
            runCatching { java.time.LocalDate.parse(it) }.getOrNull()
        } ?: return null
        val zone = ZoneId.systemDefault()
        return if (endOfDay) {
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        } else {
            date.atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }

    companion object {
        private val MAPPER = ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        /**
         * Logs the token usage per API call. OpenRouter reports prompt/completion tokens for
         * every provider uniformly; cached-prompt tokens (when the underlying provider/model
         * supports caching) come through prompt_tokens_details.cached_tokens when present.
         */
        private fun logUsage(feature: String, response: JSONObject) {
            val usage = response.optJSONObject("usage") ?: return
            val input = usage.optLong("prompt_tokens", 0)
            val output = usage.optLong("completion_tokens", 0)
            val cacheRead = usage.optJSONObject("prompt_tokens_details")
                ?.optLong("cached_tokens", 0) ?: 0L
            val model = response.optString("model").ifBlank { "unknown" }
            Log.d("AiUsage", "$feature: in=$input cacheRead=$cacheRead out=$output model=$model")
            AiUsageLog.recorder?.invoke(feature, model, input, 0L, cacheRead, output)
        }

        private const val TYPE_VALUES = "task, idea, goal, appointment, shopping_item, note"

        // Hand-written OpenAI-style function-tool definitions (OpenRouter's chat-completions
        // format), analogous to the earlier Anthropic tool schemas.
        private val SEARCH_ITEMS_TOOL = JSONObject(
            """
            {
              "type": "function",
              "function": {
                "name": "search_items",
                "description": "Search the user's existing planner entries by keyword (matches title, notes, category and tags; case-insensitive substring). Returns up to 20 entries. Use one significant word per call; try a synonym or word stem if there are no matches.",
                "parameters": {
                  "type": "object",
                  "properties": {
                    "query": { "type": "string", "description": "Keyword to search for, e.g. 'Zahnarzt'." },
                    "type": { "type": "string", "description": "Optional filter: one of $TYPE_VALUES." }
                  },
                  "required": ["query"]
                }
              }
            }
            """.trimIndent(),
        )

        private val LIST_RECENT_TOOL = JSONObject(
            """
            {
              "type": "function",
              "function": {
                "name": "list_recent",
                "description": "List the user's most recently created planner entries (newest first, up to 15). Useful for commands like 'delete the last appointment'.",
                "parameters": {
                  "type": "object",
                  "properties": {
                    "type": { "type": "string", "description": "Optional filter: one of $TYPE_VALUES." }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        private val LIST_OPEN_TOOL = JSONObject(
            """
            {
              "type": "function",
              "function": {
                "name": "list_open",
                "description": "List the user's open (not yet completed) entries, optionally of one type: tasks, appointments, shopping items, ideas, goals or knowledge notes. Dated entries come first in date order, then the undated ones. Use this for any question about what is open or on a list ('welche Aufgaben habe ich?', 'was steht auf der Einkaufsliste?', 'meine Ziele') — a keyword search is not needed for that. Returns up to 50 entries.",
                "parameters": {
                  "type": "object",
                  "properties": {
                    "type": { "type": "string", "description": "Optional filter: one of $TYPE_VALUES. Omit for everything." }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        private val LIST_AGENDA_TOOL = JSONObject(
            """
            {
              "type": "function",
              "function": {
                "name": "list_agenda",
                "description": "List the user's dated, open entries (appointments and tasks/goals with a due date) whose date falls into the given period, sorted by date. Use this for any question about a specific period ('im August', 'nächste Woche', 'dieses Wochenende') instead of relying on the context. Returns up to 50 entries.",
                "parameters": {
                  "type": "object",
                  "properties": {
                    "from": { "type": "string", "description": "Start of the period as local date YYYY-MM-DD (inclusive)." },
                    "to": { "type": "string", "description": "End of the period as local date YYYY-MM-DD (inclusive)." }
                  },
                  "required": ["from", "to"]
                }
              }
            }
            """.trimIndent(),
        )

        private fun toItemTypeOrNull(value: String?): ItemType? = when (value?.lowercase()) {
            "task" -> ItemType.TASK
            "idea" -> ItemType.IDEA
            "goal" -> ItemType.GOAL
            "appointment" -> ItemType.APPOINTMENT
            "shopping_item" -> ItemType.SHOPPING_ITEM
            "note" -> ItemType.NOTE
            else -> null
        }
    }
}

// ---- Mapping from the AI schema to the database types ----

fun ParsedItem.toItemType(): ItemType = when (type.lowercase()) {
    "idea" -> ItemType.IDEA
    "goal" -> ItemType.GOAL
    "appointment" -> ItemType.APPOINTMENT
    "shopping_item" -> ItemType.SHOPPING_ITEM
    "note" -> ItemType.NOTE
    else -> ItemType.TASK
}

fun ParsedItem.toPriority(): Priority = when (priority.lowercase()) {
    "high" -> Priority.HIGH
    "medium" -> Priority.MEDIUM
    "low" -> Priority.LOW
    else -> Priority.NONE
}

fun String?.toPriorityOrNull(): Priority? = when (this?.lowercase()) {
    "high" -> Priority.HIGH
    "medium" -> Priority.MEDIUM
    "low" -> Priority.LOW
    "none" -> Priority.NONE
    else -> null
}

fun ParsedAction.dueAtMillis(zone: ZoneId = ZoneId.systemDefault()): Long? =
    dueAt?.let {
        runCatching { LocalDateTime.parse(it).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }

fun ParsedItem.dueAtMillis(zone: ZoneId = ZoneId.systemDefault()): Long? =
    dueAt?.let {
        runCatching { LocalDateTime.parse(it).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }

fun LinkSuggestion.toRelation(): LinkRelation = when (relation.lowercase()) {
    "same_topic" -> LinkRelation.SAME_TOPIC
    "depends_on" -> LinkRelation.DEPENDS_ON
    "part_of" -> LinkRelation.PART_OF
    else -> LinkRelation.RELATED
}

fun ParsedAction.toRelationOrDefault(): LinkRelation = when (relation?.lowercase()) {
    "same_topic" -> LinkRelation.SAME_TOPIC
    "depends_on" -> LinkRelation.DEPENDS_ON
    "part_of" -> LinkRelation.PART_OF
    else -> LinkRelation.RELATED
}

/** Resolves "new:<index>" / "existing:<id>" against the freshly inserted ids. */
fun resolveLinkRef(ref: String, newIds: List<Long>): Long? {
    val parts = ref.split(":")
    if (parts.size != 2) return null
    val value = parts[1].toLongOrNull() ?: return null
    return when (parts[0]) {
        "new" -> newIds.getOrNull(value.toInt())
        "existing" -> value
        else -> null
    }
}

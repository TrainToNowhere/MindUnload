package com.app.mindunload.ai

import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlock
import com.app.mindunload.R
import com.app.mindunload.data.ItemType
import com.app.mindunload.data.LinkRelation
import com.app.mindunload.data.PlannerItem
import com.app.mindunload.data.Priority
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Model constants: cheap for structuring, strong for research/generation. */
object Models {
    const val PARSING = "claude-haiku-4-5"
    const val RESEARCH = "claude-sonnet-5"
}

class MissingApiKeyException : Exception("API key not configured")

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
 * Logs the token usage per API call. cacheRead=0 on repeated calls means:
 * caching is not kicking in (e.g. prefix below Haiku's cacheable minimum of 4096 tokens).
 */
internal fun logUsage(feature: String, response: Message) {
    val usage = response.usage()
    val input = usage.inputTokens()
    val cacheWrite = usage.cacheCreationInputTokens().orElse(0L)
    val cacheRead = usage.cacheReadInputTokens().orElse(0L)
    val output = usage.outputTokens()
    Log.d(
        "AiUsage",
        "$feature: in=$input cacheWrite=$cacheWrite cacheRead=$cacheRead out=$output",
    )
    AiUsageLog.recorder?.invoke(
        feature,
        response.model().toString(),
        input,
        cacheWrite,
        cacheRead,
        output
    )
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

/** One earlier exchange in a multi-turn chat mode — see [ClaudeService.structureThoughts]. */
data class ThoughtTurn(val userText: String, val assistantText: String)

class ClaudeService(
    private val settings: SettingsStore,
    private val prompts: Prompts,
) {

    fun client(): AnthropicClient {
        val key = settings.apiKey ?: throw MissingApiKeyException()
        return AnthropicOkHttpClient.builder().apiKey(key).build()
    }

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

        var params = MessageCreateParams.builder()
            .model(Models.PARSING)
            .maxTokens(4096L)
            .system(prompts.withLanguageRule(R.raw.prompt_parse_system))
            // Top-level caching instead of a marker on the system prompt: that alone is below
            // Haiku's 4096-token minimum and was never cached. This way the breakpoint moves
            // to the end of the growing conversation and kicks in from the second tool round.
            .cacheControl(CacheControlEphemeral.builder().build())
            // Do not use outputConfig(Class): its schema generation
            // needs reflection APIs that do not exist on Android.
            .outputConfig(OUTPUT_CONFIG)
            .addTool(SEARCH_ITEMS_TOOL)
            .addTool(LIST_RECENT_TOOL)
            .addUserMessage(userMessage)
            .build()

        val response = runToolLoop(params, tools, "parseCommand")

        val json = response.content().stream()
            .flatMap { it.text().stream() }
            .map { it.text() }
            .findFirst()
            .orElseThrow { IllegalStateException("Empty response from model") }
        MAPPER.readValue(json, ParsedCommand::class.java)
    }

    /**
     * Tool loop: answer the model's search requests against the DB until the final
     * response arrives. Round limit as a cost brake.
     */
    private suspend fun runToolLoop(
        initialParams: MessageCreateParams,
        tools: BacklogTools,
        feature: String,
    ): Message {
        var params = initialParams
        var response = client().messages().create(params)
        logUsage(feature, response)
        var rounds = 0
        while (response.stopReason().orElse(null) == StopReason.TOOL_USE && rounds < 6) {
            val results = response.content()
                .mapNotNull { it.toolUse().orElse(null) }
                .map { use ->
                    ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder()
                            .toolUseId(use.id())
                            .content(runBacklogTool(tools, use))
                            .build(),
                    )
                }
            if (results.isEmpty()) break
            params = params.toBuilder()
                .addMessage(response.toParam())
                .addUserMessageOfBlockParams(results)
                .build()
            response = client().messages().create(params)
            logUsage("$feature/round${rounds + 1}", response)
            rounds++
        }
        return response
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

        val params = MessageCreateParams.builder()
            .model(Models.PARSING)
            .maxTokens(2048L)
            .system(prompts.withLanguageRule(R.raw.prompt_ask_system))
            .addTool(SEARCH_ITEMS_TOOL)
            .addTool(LIST_RECENT_TOOL)
            .addTool(LIST_OPEN_TOOL)
            .addTool(LIST_AGENDA_TOOL)
            .addUserMessage(userMessage)
            .build()
        val response = runToolLoop(params, tools, "askChat")
        response.content()
            .mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
            .joinToString("\n")
            .ifBlank { throw IllegalStateException("Empty answer") }
    }

    /**
     * Chat mode "Structure thoughts": turns a (typically long, transcribed) stream of
     * thought into a structured Markdown note with an action-item checklist — and,
     * because it is a real conversation, can ask a follow-up question instead of
     * guessing, or revise the note once the user replies. [history] is the earlier
     * turns of this same conversation, oldest first. Runs on the cheap model — this is
     * reformatting and light dialogue, not research or generation.
     */
    suspend fun structureThoughts(
        text: String,
        history: List<ThoughtTurn> = emptyList(),
    ): String = withContext(Dispatchers.IO) {
        val builder = MessageCreateParams.builder()
            .model(Models.PARSING)
            .maxTokens(4096L)
            .system(prompts.withLanguageRule(R.raw.prompt_structure_system))
        history.forEach { turn ->
            builder.addUserMessage(turn.userText).addAssistantMessage(turn.assistantText)
        }
        val params = builder.addUserMessage(text).build()
        val response = client().messages().create(params)
        logUsage("structureThoughts", response)
        response.content()
            .mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
            .joinToString("\n")
            .ifBlank { throw IllegalStateException("Empty structured note") }
    }

    /** Executes a search_items/list_recent call from the model and formats the result compactly. */
    private suspend fun runBacklogTool(tools: BacklogTools, use: ToolUseBlock): String {
        val input = runCatching {
            @Suppress("UNCHECKED_CAST")
            use._input().convert(Map::class.java) as Map<String, Any?>
        }.getOrNull() ?: emptyMap()
        val type = toItemTypeOrNull(input["type"] as? String)
        val items = when (use.name()) {
            "search_items" -> {
                val query = (input["query"] as? String)?.trim().orEmpty()
                if (query.isEmpty()) return "error: missing query"
                tools.search(query, type)
            }

            "list_recent" -> tools.recent(type)

            "list_open" -> tools.open(type)

            "list_agenda" -> {
                val from = parseToolDate(input["from"] as? String, endOfDay = false)
                    ?: return "error: invalid or missing 'from' (expected YYYY-MM-DD)"
                val to = parseToolDate(input["to"] as? String, endOfDay = true)
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

        val params = MessageCreateParams.builder()
            .model(Models.PARSING)
            .maxTokens(2048L)
            .outputConfig(CLEANUP_OUTPUT_CONFIG)
            .addUserMessage(prompt)
            .build()

        val response = client().messages().create(params)
        logUsage("suggestCleanup", response)
        val json = response.content().stream()
            .flatMap { it.text().stream() }
            .map { it.text() }
            .findFirst()
            .orElseThrow { IllegalStateException("Empty response from model") }
        MAPPER.readValue(json, CleanupResult::class.java).suggestions
    }

    /** Small helper: a free-text request to the parsing model (Haiku). */
    private suspend fun freeText(
        prompt: String,
        maxTokens: Long,
        model: String = Models.PARSING
    ): String =
        withContext(Dispatchers.IO) {
            val params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .addUserMessage(prompt)
                .build()
            val response = client().messages().create(params)
            logUsage("freeText/$model", response)
            response.content()
                .mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
                .joinToString("\n")
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
    ): String {
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
        return freeText(prompt, 2048L, model = Models.RESEARCH)
            .ifBlank { throw IllegalStateException("Empty review") }
    }

    /**
     * Image-to-text: reads out everything legible on a photo (receipt, note, poster,
     * screenshot) so the normal chat pipeline can work on plain text afterwards.
     * Runs on the cheap model — Haiku sees images and OCR needs no reasoning depth.
     *
     * [imageBase64] is the JPEG stored by [com.app.mindunload.data.Attachments], already
     * scaled down to Claude's useful maximum edge.
     */
    suspend fun extractTextFromImage(
        imageBase64: String,
        mediaType: String = Base64ImageSource.MediaType.IMAGE_JPEG.toString(),
    ): String = withContext(Dispatchers.IO) {
        val image = ImageBlockParam.builder()
            .source(
                Base64ImageSource.builder()
                    .data(imageBase64)
                    .mediaType(Base64ImageSource.MediaType.of(mediaType))
                    .build(),
            )
            .build()
        val params = MessageCreateParams.builder()
            .model(Models.PARSING)
            .maxTokens(2048L)
            .addUserMessageOfBlockParams(
                listOf(
                    ContentBlockParam.ofImage(image),
                    ContentBlockParam.ofText(
                        TextBlockParam.builder()
                            .text(prompts.withLanguageRule(R.raw.prompt_image_text))
                            .build(),
                    ),
                ),
            )
            .build()

        val response = client().messages().create(params)
        logUsage("extractTextFromImage", response)
        response.content()
            .mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
            .joinToString("\n")
            .trim()
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

        val params = MessageCreateParams.builder()
            .model(Models.PARSING)
            .maxTokens(768L)
            .addUserMessage(prompt)
            .build()

        val response = client().messages().create(params)
        logUsage("generateBriefing", response)
        response.content()
            .mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
            .joinToString("\n")
            .ifBlank { throw IllegalStateException("Empty briefing") }
    }

    companion object {
        private val MAPPER = ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        private fun outputConfigOf(schemaJson: String): OutputConfig {
            @Suppress("UNCHECKED_CAST")
            val schemaMap = MAPPER.readValue(schemaJson, Map::class.java) as Map<String, Any?>
            return OutputConfig.builder()
                .format(
                    JsonOutputFormat.builder()
                        .type(JsonValue.from("json_schema"))
                        .schema(
                            JsonOutputFormat.Schema.builder()
                                .putAllAdditionalProperties(schemaMap.mapValues { JsonValue.from(it.value) })
                                .build(),
                        )
                        .build(),
                )
                .build()
        }

        private val OUTPUT_CONFIG: OutputConfig by lazy { outputConfigOf(PARSED_COMMAND_SCHEMA_JSON) }
        private val CLEANUP_OUTPUT_CONFIG: OutputConfig by lazy { outputConfigOf(CLEANUP_SCHEMA_JSON) }

        private const val TYPE_VALUES = "task, idea, goal, appointment, shopping_item, note"

        // Hand-written tool input schemas (same Android pitfall as with the output schema:
        // no schema generation via reflection).
        private val SEARCH_ITEMS_TOOL: Tool = Tool.builder()
            .name("search_items")
            .description(
                "Search the user's existing planner entries by keyword (matches title, notes, " +
                        "category and tags; case-insensitive substring). Returns up to 20 entries. " +
                        "Use one significant word per call; try a synonym or word stem if there are no matches.",
            )
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(
                        Tool.InputSchema.Properties.builder()
                            .putAdditionalProperty(
                                "query",
                                JsonValue.from(
                                    mapOf(
                                        "type" to "string",
                                        "description" to "Keyword to search for, e.g. 'Zahnarzt'.",
                                    ),
                                ),
                            )
                            .putAdditionalProperty(
                                "type",
                                JsonValue.from(
                                    mapOf(
                                        "type" to "string",
                                        "description" to "Optional filter: one of $TYPE_VALUES.",
                                    ),
                                ),
                            )
                            .build(),
                    )
                    .addRequired("query")
                    .build(),
            )
            .build()

        private val LIST_RECENT_TOOL: Tool = Tool.builder()
            .name("list_recent")
            .description(
                "List the user's most recently created planner entries (newest first, up to 15). " +
                        "Useful for commands like 'delete the last appointment'.",
            )
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(
                        Tool.InputSchema.Properties.builder()
                            .putAdditionalProperty(
                                "type",
                                JsonValue.from(
                                    mapOf(
                                        "type" to "string",
                                        "description" to "Optional filter: one of $TYPE_VALUES.",
                                    ),
                                ),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

        private val LIST_OPEN_TOOL: Tool = Tool.builder()
            .name("list_open")
            .description(
                "List the user's open (not yet completed) entries, optionally of one type: " +
                        "tasks, appointments, shopping items, ideas, goals or knowledge notes. " +
                        "Dated entries come first in date order, then the undated ones. Use this " +
                        "for any question about what is open or on a list ('welche Aufgaben habe " +
                        "ich?', 'was steht auf der Einkaufsliste?', 'meine Ziele') — a keyword " +
                        "search is not needed for that. Returns up to 50 entries.",
            )
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(
                        Tool.InputSchema.Properties.builder()
                            .putAdditionalProperty(
                                "type",
                                JsonValue.from(
                                    mapOf(
                                        "type" to "string",
                                        "description" to "Optional filter: one of $TYPE_VALUES. Omit for everything.",
                                    ),
                                ),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

        private val LIST_AGENDA_TOOL: Tool = Tool.builder()
            .name("list_agenda")
            .description(
                "List the user's dated, open entries (appointments and tasks/goals with a due " +
                        "date) whose date falls into the given period, sorted by date. Use this " +
                        "for any question about a specific period ('im August', 'nächste Woche', " +
                        "'dieses Wochenende') instead of relying on the context. Returns up to 50 entries.",
            )
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(
                        Tool.InputSchema.Properties.builder()
                            .putAdditionalProperty(
                                "from",
                                JsonValue.from(
                                    mapOf(
                                        "type" to "string",
                                        "description" to "Start of the period as local date YYYY-MM-DD (inclusive).",
                                    ),
                                ),
                            )
                            .putAdditionalProperty(
                                "to",
                                JsonValue.from(
                                    mapOf(
                                        "type" to "string",
                                        "description" to "End of the period as local date YYYY-MM-DD (inclusive).",
                                    ),
                                ),
                            )
                            .build(),
                    )
                    .addRequired("from")
                    .addRequired("to")
                    .build(),
            )
            .build()

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

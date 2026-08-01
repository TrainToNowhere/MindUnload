package com.app.ai.planner.ai

import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.WebFetchTool20260209
import com.anthropic.models.messages.WebSearchTool20260209
import com.app.ai.planner.data.PlannerItem
import com.app.ai.planner.data.ResearchNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A finished web research: summary text plus the sources as a JSON array of {"title","url"}. */
data class ResearchResult(val summary: String, val sources: JSONArray)

class ResearchService(
    private val claude: ClaudeService,
    private val prompts: Prompts,
) {

    /**
     * Researches a backlog topic via web search (server tools, running at Anthropic)
     * and returns a storable [ResearchNote].
     */
    suspend fun research(item: PlannerItem): ResearchNote {
        val prompt = buildString {
            appendLine("Research the following topic from the user's personal backlog:")
            appendLine()
            appendLine("Title: ${item.title}")
            item.notes?.let { appendLine("Notes: $it") }
            item.category?.let { appendLine("Category: $it") }
            if (item.tags.isNotEmpty()) appendLine("Tags: ${item.tags.joinToString(", ")}")
            appendLine()
            appendLine(prompts.withLanguageRule(com.app.ai.planner.R.raw.prompt_research))
        }
        val result = runResearch(prompt, "research")
        return ResearchNote(
            itemId = item.id,
            summary = result.summary,
            sourcesJson = result.sources.toString(),
        )
    }

    /**
     * Researches a free topic from the chat — without an existing entry. The result is
     * only shown in the chat; the user decides whether it becomes a knowledge entry.
     */
    suspend fun researchTopic(topic: String): ResearchResult {
        val prompt = buildString {
            appendLine("Research the following topic for the user:")
            appendLine()
            appendLine(topic)
            appendLine()
            appendLine(prompts.withLanguageRule(com.app.ai.planner.R.raw.prompt_research))
        }
        return runResearch(prompt, "researchTopic")
    }

    private suspend fun runResearch(
        prompt: String,
        feature: String,
    ): ResearchResult = withContext(Dispatchers.IO) {
        val client = claude.client()
        var params = MessageCreateParams.builder()
            .model(Models.RESEARCH)
            .maxTokens(4096L)
            .addTool(WebSearchTool20260209.builder().maxUses(3L).build())
            // Without maxContentTokens entire web pages end up untrimmed in the context —
            // a capped excerpt is enough for a 400-word summary.
            .addTool(WebFetchTool20260209.builder().maxUses(2L).maxContentTokens(10_000L).build())
            // The breakpoint moves to the end of the conversation: pause_turn continuations
            // read the already processed search results from the cache instead of at full price.
            .cacheControl(CacheControlEphemeral.builder().build())
            .addUserMessage(prompt)
            .build()

        var response = client.messages().create(params)
        logUsage(feature, response)
        // Server tools may interrupt with pause_turn — then continue unchanged.
        var continuations = 0
        while (response.stopReason().orElse(null) == StopReason.PAUSE_TURN && continuations < 5) {
            params = params.toBuilder().addMessage(response.toParam()).build()
            response = client.messages().create(params)
            logUsage("$feature/cont${continuations + 1}", response)
            continuations++
        }

        // Only the text AFTER the last tool/thinking block is the actual answer —
        // text blocks before it are interim narration ("Ich suche zunächst…"). The fragments
        // are continuations split at citation boundaries, not lines: join without separator.
        val content = response.content()
        fun joinedText(blocks: List<com.anthropic.models.messages.ContentBlock>): String =
            blocks.mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
                .joinToString("")

        val lastNonText = content.indexOfLast { !it.text().isPresent }
        val text = joinedText(content.drop(lastNonText + 1))
            .ifBlank { joinedText(content) }
            .ifBlank { throw IllegalStateException("Empty research result") }

        ResearchResult(summary = stripSourcesSection(text), sources = extractSources(text))
    }

    /**
     * Removes the trailing "Quellen:" section from the summary — the UI already shows
     * the sources as their own clickable section from [extractSources].
     */
    private fun stripSourcesSection(text: String): String {
        // The prompt demands the literal label "Quellen:", but tolerate "Sources:" too.
        val header = Regex("""^#{0,6}\s*\**(Quellen|Sources)\**\s*:?.*$""", RegexOption.IGNORE_CASE)
        val idx = text.lines().indexOfLast { header.matches(it.trim()) }
        if (idx < 0) return text
        return text.lines().take(idx).joinToString("\n").trimEnd()
    }

    /** Extracts URLs (with an optional title prefix) from the sources section. */
    private fun extractSources(text: String): JSONArray {
        val array = JSONArray()
        val urlRegex = Regex("""https?://[^\s)\]"']+""")
        val seen = mutableSetOf<String>()
        text.lineSequence().forEach { line ->
            val url = urlRegex.find(line)?.value?.trimEnd('.', ',') ?: return@forEach
            if (!seen.add(url)) return@forEach
            val title = line.substringBefore(url)
                .trim(' ', '-', '*', '•', '—', '[', ']', '(', ':')
                .ifBlank { url }
            array.put(JSONObject().put("title", title).put("url", url))
        }
        return array
    }
}

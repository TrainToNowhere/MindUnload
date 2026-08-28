package com.app.mindunload.ai

import com.app.mindunload.data.PlannerItem
import com.app.mindunload.data.ResearchNote
import org.json.JSONArray
import org.json.JSONObject

/** A finished web research: summary text plus the sources as a JSON array of {"title","url"}. */
data class ResearchResult(val summary: String, val sources: JSONArray)

class ResearchService(
    private val ai: AiService,
    private val prompts: Prompts,
) {

    /**
     * Researches a backlog topic via OpenRouter's web-search plugin and returns a
     * storable [ResearchNote].
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
            appendLine(prompts.withLanguageRule(com.app.mindunload.R.raw.prompt_research))
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
     * [history] are the earlier turns of the same research conversation, so a follow-up
     * ("und was kostet das in Deutschland?") builds on what was already found.
     */
    suspend fun researchTopic(
        topic: String,
        history: List<ConversationTurn> = emptyList(),
    ): ResearchResult {
        val prompt = buildString {
            appendLine("Research the following topic for the user:")
            appendLine()
            appendLine(topic)
            appendLine()
            appendLine(prompts.withLanguageRule(com.app.mindunload.R.raw.prompt_research))
        }
        return runResearch(prompt, "researchTopic", history)
    }

    private suspend fun runResearch(
        prompt: String,
        feature: String,
        history: List<ConversationTurn> = emptyList(),
    ): ResearchResult {
        val text = ai.researchAnswer(prompt, feature, history)
        return ResearchResult(summary = stripSourcesSection(text), sources = extractSources(text))
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

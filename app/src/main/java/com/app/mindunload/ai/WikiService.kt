package com.app.mindunload.ai

import com.app.mindunload.data.PlannerItem

/**
 * Answers natural-language questions about the user's knowledge entries (ItemType.NOTE) —
 * no web search, only from the provided context. Modeled on [ResearchService], but without
 * server tools and without persistence (every query is fresh).
 */
class WikiService(
    private val ai: AiService,
    private val prompts: Prompts,
) {

    suspend fun answer(question: String, notes: List<PlannerItem>): String {
        val context = if (notes.isEmpty()) {
            "no knowledge entries available"
        } else {
            notes.joinToString("\n\n") { note ->
                buildString {
                    append("Title: ${note.title}")
                    note.notes?.let { append("\nDetails: $it") }
                    note.category?.let { append("\nCategory: $it") }
                    if (note.tags.isNotEmpty()) append("\nTags: ${note.tags.joinToString(", ")}")
                }
            }
        }
        val prompt = """
            These are the user's saved knowledge entries (their personal wiki):

            $context

            Question: $question

            ${prompts.withLanguageRule(com.app.mindunload.R.raw.prompt_wiki)}
        """.trimIndent()

        return ai.quickAnswer(prompt)
    }
}

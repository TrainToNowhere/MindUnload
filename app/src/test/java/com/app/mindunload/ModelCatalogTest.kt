package com.app.mindunload

import com.app.mindunload.ai.OpenRouterModels
import com.app.mindunload.ui.formatPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The model picker in Settings is fed by a live OpenRouter response, so the filtering
 * runs against data nobody here controls: providers rename models, add ":free" variants
 * and drop capabilities. This pins the response shape the picker relies on, including
 * the price conversion (OpenRouter quotes per token, the picker shows per million).
 */
class ModelCatalogTest {

    private fun entry(
        id: String,
        name: String,
        created: Long = 0L,
        params: String = """["tools", "structured_outputs"]""",
        pricing: String? = """{"prompt": "0.0000008", "completion": "0.000004"}""",
    ) = buildString {
        append("""{"id": "$id", "name": "$name", "created": $created""")
        append(""", "supported_parameters": $params""")
        if (pricing != null) append(""", "pricing": $pricing""")
        append("}")
    }

    private fun catalog(vararg entries: String) =
        OpenRouterModels.parseCatalog("""{"data": [${entries.joinToString(",")}]}""")

    @Test
    fun `provider and label are split from the display name`() {
        val model =
            catalog(entry("anthropic/claude-haiku-4.5", "Anthropic: Claude Haiku 4.5")).single()
        assertEquals("anthropic/claude-haiku-4.5", model.id)
        assertEquals("Anthropic", model.provider)
        assertEquals("Claude Haiku 4.5", model.label)
    }

    @Test
    fun `prices are converted to dollars per million tokens`() {
        val model = catalog(entry("openai/gpt-5", "OpenAI: GPT-5")).single()
        assertEquals(0.8, model.inputPricePerMillion!!, 1e-9)
        assertEquals(4.0, model.outputPricePerMillion!!, 1e-9)
    }

    @Test
    fun `a free model keeps its zero price, a missing one stays null`() {
        val free = catalog(
            entry(
                "google/gemma",
                "Google: Gemma",
                pricing = """{"prompt": "0", "completion": "0"}"""
            ),
        ).single()
        assertEquals(0.0, free.inputPricePerMillion!!, 1e-9)

        val unpriced = catalog(entry("google/gemma", "Google: Gemma", pricing = null)).single()
        assertNull(unpriced.inputPricePerMillion)
        assertNull(unpriced.outputPricePerMillion)
    }

    @Test
    fun `models from unknown providers are dropped`() {
        val models = catalog(
            entry("anthropic/claude-haiku-4.5", "Anthropic: Claude Haiku 4.5"),
            entry("someone/mystery-model", "Someone: Mystery"),
        )
        assertEquals(listOf("anthropic/claude-haiku-4.5"), models.map { it.id })
    }

    @Test
    fun `variant suffixes are not offered as separate models`() {
        val models = catalog(
            entry("anthropic/claude-haiku-4.5", "Anthropic: Claude Haiku 4.5"),
            entry("anthropic/claude-haiku-4.5:free", "Anthropic: Claude Haiku 4.5 (free)"),
            entry("anthropic/claude-haiku-4.5:batch", "Anthropic: Claude Haiku 4.5 (batch)"),
        )
        assertEquals(listOf("anthropic/claude-haiku-4.5"), models.map { it.id })
    }

    @Test
    fun `models without tools or structured output are unusable for the pipeline`() {
        val models = catalog(
            entry("openai/gpt-5", "OpenAI: GPT-5", params = """["tools", "response_format"]"""),
            entry("openai/no-tools", "OpenAI: No Tools", params = """["response_format"]"""),
            entry("openai/no-json", "OpenAI: No Json", params = """["tools"]"""),
            entry("openai/nothing", "OpenAI: Nothing", params = "[]"),
        )
        assertEquals(listOf("openai/gpt-5"), models.map { it.id })
    }

    @Test
    fun `only the newest few models per provider are kept`() {
        val models = catalog(
            *(1..6).map { entry("openai/gpt-$it", "OpenAI: GPT $it", created = it.toLong()) }
                .toTypedArray(),
        )
        assertEquals(4, models.size)
        // The four highest "created" values, whatever their label order in the result.
        assertEquals(
            setOf("openai/gpt-6", "openai/gpt-5", "openai/gpt-4", "openai/gpt-3"),
            models.map { it.id }.toSet(),
        )
    }

    @Test
    fun `the list is grouped by provider and sorted by label`() {
        val models = catalog(
            entry("openai/gpt-5", "OpenAI: GPT-5"),
            entry("anthropic/claude-sonnet-5", "Anthropic: Claude Sonnet 5"),
            entry("anthropic/claude-haiku-4.5", "Anthropic: Claude Haiku 4.5"),
        )
        assertEquals(
            listOf("Claude Haiku 4.5", "Claude Sonnet 5", "GPT-5"),
            models.map { it.label },
        )
    }

    @Test
    fun `labelFor falls back to the raw id for a model outside the catalog`() {
        val models = catalog(entry("anthropic/claude-haiku-4.5", "Anthropic: Claude Haiku 4.5"))
        assertEquals(
            "Anthropic · Claude Haiku 4.5",
            OpenRouterModels.labelFor(models.single().id, models)
        )
        assertEquals("some/retired-model", OpenRouterModels.labelFor("some/retired-model", models))
    }

    @Test
    fun `an empty or unexpected response yields an empty catalog`() {
        assertTrue(OpenRouterModels.parseCatalog("""{"data": []}""").isEmpty())
    }

    @Test
    fun `prices below a dollar keep the digit that distinguishes them`() {
        // Two decimals would render both of these as "$0.00".
        assertEquals("$0.002", formatPrice(0.002))
        assertEquals("$0.080", formatPrice(0.08))
        assertEquals("$1.20", formatPrice(1.2))
        assertEquals("$15.00", formatPrice(15.0))
        assertEquals("?", formatPrice(null))
    }
}

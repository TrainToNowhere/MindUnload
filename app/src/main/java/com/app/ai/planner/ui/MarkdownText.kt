package com.app.ai.planner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.app.ai.planner.ui.theme.PlannerColors

/**
 * Minimal Markdown renderer for research answers: headings (#),
 * bullet lists (-/*), dividers (---) and **bold**/`code`/[link](url) inline.
 * Deliberately without an external library — covers exactly what the model produces.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                trimmed.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> HorizontalDivider()
                trimmed.startsWith("#") -> {
                    val level = trimmed.takeWhile { it == '#' }.length
                    Text(
                        inlineMarkdown(trimmed.dropWhile { it == '#' }.trim()),
                        style = if (level <= 2) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                    )
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                    Row {
                        Text("•  ", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            inlineMarkdown(trimmed.drop(2)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                else -> Text(inlineMarkdown(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end == -1) {
                    append(text[i]); i++
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(
                            text.substring(
                                i + 2,
                                end
                            )
                        )
                    }
                    i = end + 2
                }
            }

            // [Title](https://…) — opens in the browser on tap.
            text[i] == '[' -> {
                val labelEnd = text.indexOf("](", i)
                val urlEnd = if (labelEnd == -1) -1 else text.indexOf(')', labelEnd + 2)
                if (labelEnd == -1 || urlEnd == -1) {
                    append(text[i]); i++
                } else {
                    withLink(
                        LinkAnnotation.Url(
                            text.substring(labelEnd + 2, urlEnd),
                            TextLinkStyles(SpanStyle(color = PlannerColors.primary)),
                        ),
                    ) {
                        append(text.substring(i + 1, labelEnd))
                    }
                    i = urlEnd + 1
                }
            }

            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end == -1) {
                    append(text[i]); i++
                } else {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(
                            text.substring(
                                i + 1,
                                end
                            )
                        )
                    }
                    i = end + 1
                }
            }

            else -> {
                append(text[i]); i++
            }
        }
    }
}

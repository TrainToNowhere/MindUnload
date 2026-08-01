package com.app.mindunload.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.mindunload.R
import com.app.mindunload.ui.theme.PlannerColors
import java.util.Locale

/**
 * API usage dashboard: token consumption and estimated cost of the current month
 * (counted locally from the API responses) plus a configurable monthly budget.
 */
@Composable
fun UsageScreen(onBack: () -> Unit, viewModel: UsageViewModel = viewModel()) {
    val rows by viewModel.rows.collectAsState()
    val budget by viewModel.budgetUsd.collectAsState()
    val totalCost = rows.sumOf { it.costUsd }
    val totalCalls = rows.sumOf { it.calls }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp, 14.dp, 20.dp, 24.dp),
    ) {
        BackHeader(onBack = onBack)
        Text(
            stringResource(R.string.usage_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic),
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            stringResource(R.string.usage_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = PlannerColors.muted,
        )

        // Monthly overview: cost, budget, remaining budget
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(PlannerColors.darkCard)
                .padding(18.dp),
        ) {
            Text(
                stringResource(R.string.usage_month_section).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = PlannerColors.darkCardMuted,
            )
            Text(
                formatUsd(totalCost),
                style = MaterialTheme.typography.headlineMedium,
                color = PlannerColors.onDarkCard,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                stringResource(R.string.usage_of_budget, formatUsd(budget.toDouble())),
                style = MaterialTheme.typography.bodySmall,
                color = PlannerColors.darkCardMuted,
            )
            LinearProgressIndicator(
                progress = {
                    if (budget > 0f) (totalCost / budget).toFloat().coerceIn(0f, 1f) else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
            val remaining = budget - totalCost
            Text(
                stringResource(R.string.usage_remaining, formatUsd(remaining)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (remaining < 0) PlannerColors.soon else PlannerColors.onDarkCard,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.usage_calls, totalCalls),
                style = MaterialTheme.typography.bodySmall,
                color = PlannerColors.darkCardMuted,
            )
        }

        // Breakdown per model
        Column(Modifier.padding(top = 20.dp)) {
            SectionLabel(stringResource(R.string.usage_by_model))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PlannerColors.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (rows.isEmpty()) {
                    Text(stringResource(R.string.usage_empty), color = PlannerColors.faint)
                }
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = PlannerColors.divider)
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                row.model,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                formatUsd(row.costUsd),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            stringResource(
                                R.string.usage_tokens_line,
                                formatTokens(row.inputTokens),
                                formatTokens(row.outputTokens),
                                formatTokens(row.cacheWriteTokens + row.cacheReadTokens),
                                row.calls,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = PlannerColors.mutedLight,
                        )
                    }
                }
            }
        }

        // Budget einstellen
        Column(Modifier.padding(top = 20.dp)) {
            SectionLabel(stringResource(R.string.usage_budget_section))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PlannerColors.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                var budgetInput by remember(budget) { mutableStateOf(formatPlain(budget)) }
                var budgetSaved by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = {
                        budgetInput = it
                        budgetSaved = false
                    },
                    label = { Text(stringResource(R.string.usage_budget_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        budgetInput.replace(',', '.').toFloatOrNull()?.let {
                            viewModel.setBudget(it)
                            budgetSaved = true
                        }
                    },
                    enabled = budgetInput.replace(',', '.').toFloatOrNull() != null,
                ) {
                    Text(stringResource(R.string.action_save))
                }
                if (budgetSaved) {
                    Text(
                        stringResource(R.string.settings_key_saved),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Text(
            stringResource(R.string.usage_price_note),
            style = MaterialTheme.typography.bodySmall,
            color = PlannerColors.faint,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/** Small amounts with more decimal places, otherwise it would just read "0,00 $". */
private fun formatUsd(value: Double): String =
    if (value != 0.0 && kotlin.math.abs(value) < 0.1) {
        String.format(Locale.GERMAN, "%.4f $", value)
    } else {
        String.format(Locale.GERMAN, "%.2f $", value)
    }

private fun formatPlain(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()

private fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000 -> String.format(Locale.GERMAN, "%.1f M", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format(Locale.GERMAN, "%.1f k", tokens / 1_000.0)
    else -> tokens.toString()
}

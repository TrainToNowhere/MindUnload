package com.app.mindunload.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.mindunload.R
import com.app.mindunload.ui.theme.PlannerColors

/**
 * Usage statistics: how many entries of each type were created and completed in a
 * selectable period (bar chart), plus the full, uncapped activity log for that same
 * period. Distinct from [UsageScreen], which tracks API token cost — this screen is
 * about the user's own planner activity.
 */
@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = viewModel()) {
    val range by viewModel.range.collectAsState()
    val typeStats by viewModel.typeStats.collectAsState()
    val detailed by viewModel.detailedActivity.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp, 14.dp, 20.dp, 24.dp),
    ) {
        BackHeader(title = stringResource(R.string.stats_title), onBack = onBack)
        Text(
            stringResource(R.string.stats_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = PlannerColors.muted,
        )

        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsRange.entries.forEach { r ->
                FilterChip(
                    selected = range == r,
                    onClick = { viewModel.setRange(r) },
                    label = { Text(stringResource(rangeLabelRes(r))) },
                )
            }
        }

        val hasActivity = typeStats.any { it.created > 0 || it.done > 0 }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PlannerColors.surface)
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LegendDot(PlannerColors.primary, stringResource(R.string.stats_created_legend))
                LegendDot(PlannerColors.text, stringResource(R.string.stats_done_legend))
            }
            if (hasActivity) {
                StatsBarChart(typeStats, modifier = Modifier.padding(top = 20.dp))
            } else {
                SmallEmptyHint(stringResource(R.string.stats_empty))
            }
        }

        Column(Modifier.padding(top = 20.dp)) {
            SectionLabel(stringResource(R.string.stats_detail_header))
            if (detailed.isEmpty()) {
                SmallEmptyHint(stringResource(R.string.stats_empty))
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PlannerColors.surface)
                        .padding(vertical = 6.dp, horizontal = 15.dp),
                ) {
                    detailed.forEach { entry -> ActivityEntryRow(entry) }
                }
            }
        }
    }
}

private fun rangeLabelRes(range: StatsRange): Int = when (range) {
    StatsRange.WEEK -> R.string.review_week
    StatsRange.MONTH -> R.string.review_month
    StatsRange.YEAR -> R.string.review_year
}

/** Small color swatch + label — the chart's legend, one per series. */
@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = PlannerColors.muted)
    }
}

/**
 * One pair of bars (created/done) per entry type, grouped by type icon underneath —
 * the type icons already exist and are more legible at a glance than six more colors
 * would be, so identity comes from icon + label, not from a wide categorical palette.
 */
@Composable
private fun StatsBarChart(stats: List<TypeStat>, modifier: Modifier = Modifier) {
    val maxValue = stats.maxOf { maxOf(it.created, it.done) }.coerceAtLeast(1)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        stats.forEach { stat ->
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    StatsBar(
                        fraction = stat.created / maxValue.toFloat(),
                        color = PlannerColors.primary
                    )
                    Spacer(Modifier.width(3.dp))
                    StatsBar(fraction = stat.done / maxValue.toFloat(), color = PlannerColors.text)
                }
                Spacer(Modifier.height(6.dp))
                TypeIcon(stat.type, tint = PlannerColors.muted, modifier = Modifier.size(16.dp))
                Text(
                    "${stat.created} · ${stat.done}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PlannerColors.faint,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** One bar, growing from the baseline; only the far end is rounded. */
@Composable
private fun StatsBar(fraction: Float, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .width(10.dp)
            .fillMaxHeight(fraction.coerceIn(0.015f, 1f))
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(color),
    )
}

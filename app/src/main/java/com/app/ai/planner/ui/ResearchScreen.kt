package com.app.ai.planner.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.ai.planner.R
import com.app.ai.planner.ui.theme.PlannerColors
import org.json.JSONArray

@Composable
fun ResearchScreen(noteId: Long, onBack: () -> Unit, viewModel: DetailViewModel = viewModel()) {
    val note by viewModel.researchNote(noteId).collectAsState(initial = null)
    val context = LocalContext.current
    val current = note ?: return

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp, 14.dp, 20.dp, 24.dp),
    ) {
        BackHeader(label = stringResource(R.string.research_back), onBack = onBack)
        Text(
            stringResource(R.string.research_label, formatDateTime(current.createdAt)).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = PlannerColors.muted,
            modifier = Modifier.padding(top = 10.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PlannerColors.surface)
                .padding(16.dp),
        ) {
            MarkdownText(current.summary)
        }

        val sources = runCatching { JSONArray(current.sourcesJson) }.getOrNull()
        if (sources != null && sources.length() > 0) {
            Column(Modifier.padding(top = 20.dp)) {
                SectionLabel(stringResource(R.string.sources_header))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlannerColors.surface)
                        .padding(horizontal = 15.dp),
                ) {
                    for (i in 0 until sources.length()) {
                        val source = sources.getJSONObject(i)
                        val url = source.optString("url")
                        Text(
                            source.optString("title", url),
                            color = PlannerColors.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                url.toUri()
                                            )
                                        )
                                    }
                                }
                                .padding(vertical = 10.dp),
                        )
                        if (i != sources.length() - 1) HorizontalDivider(color = PlannerColors.divider)
                    }
                }
            }
        }

        val fallbackTitle = stringResource(R.string.research_header)
        val promoted = stringResource(R.string.research_promoted)
        val promoteFailed = stringResource(R.string.research_promote_failed)
        val feedback = LocalFeedback.current
        // Once saved the button stays disabled: a second tap would silently create a duplicate.
        var alreadyPromoted by remember(current.id) { mutableStateOf(false) }
        Button(
            onClick = {
                viewModel.promoteToWiki(
                    current.summary.lineSequence().firstOrNull()?.trimStart('#', ' ')?.take(60)
                        ?: fallbackTitle, current
                ) { ok ->
                    alreadyPromoted = ok
                    feedback.show(if (ok) promoted else promoteFailed, long = !ok)
                }
            },
            enabled = !alreadyPromoted,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(
                stringResource(
                    if (alreadyPromoted) R.string.research_promoted_button else R.string.research_promote,
                ),
            )
        }
    }
}

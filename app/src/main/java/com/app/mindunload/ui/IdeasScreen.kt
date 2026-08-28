package com.app.mindunload.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.mindunload.R
import com.app.mindunload.data.ItemType
import com.app.mindunload.ui.theme.PlannerColors

@Composable
fun IdeasScreen(
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    viewModel: ItemsViewModel = viewModel()
) {
    val ideas by viewModel.items(ItemType.IDEA).collectAsState(initial = emptyList())
    val showDone by viewModel.showDone.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp, 14.dp, 20.dp, 24.dp),
    ) {
        BackHeader(title = stringResource(R.string.drawer_ideas), onBack = onBack)
        Text(
            stringResource(R.string.ideas_subtitle, ideas.size),
            style = MaterialTheme.typography.bodySmall,
            color = PlannerColors.muted
        )

        ShowDoneFilter(
            checked = showDone,
            onCheckedChange = viewModel::setShowDone,
            modifier = Modifier.padding(top = 12.dp),
        )

        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ideas.forEach { idea ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlannerColors.surface)
                        .clickable { onItemClick(idea.id) }
                        .padding(start = 4.dp, end = 15.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = idea.done,
                        onCheckedChange = { viewModel.setDone(idea, it) },
                    )
                    Column(Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)) {
                        Text(
                            idea.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            textDecoration = if (idea.done) TextDecoration.LineThrough else null,
                            color = animateDoneColor(isDone = idea.done, base = PlannerColors.text).value,
                        )
                        CardMetaRow(idea)
                    }
                }
            }
            if (ideas.isEmpty()) EmptyState(
                message = stringResource(R.string.ideas_empty),
                icon = { IdeasIcon(tint = PlannerColors.faint) },
            )
        }
    }
}

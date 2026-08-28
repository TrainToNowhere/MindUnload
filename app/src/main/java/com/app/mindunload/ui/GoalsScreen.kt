package com.app.mindunload.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.produceState
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
import com.app.mindunload.data.PlannerItem
import com.app.mindunload.ui.theme.PlannerColors

@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    viewModel: ItemsViewModel = viewModel()
) {
    val goals by viewModel.items(ItemType.GOAL).collectAsState(initial = emptyList())
    val showDone by viewModel.showDone.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp, 14.dp, 20.dp, 24.dp),
    ) {
        BackHeader(title = stringResource(R.string.drawer_goals), onBack = onBack)
        Text(
            stringResource(R.string.goals_subtitle, goals.count { !it.done }),
            style = MaterialTheme.typography.bodySmall,
            color = PlannerColors.muted
        )

        ShowDoneFilter(
            checked = showDone,
            onCheckedChange = viewModel::setShowDone,
            modifier = Modifier.padding(top = 12.dp),
        )

        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            goals.forEach { goal -> GoalCard(goal, viewModel, onClick = { onItemClick(goal.id) }) }
            if (goals.isEmpty()) EmptyState(
                message = stringResource(R.string.goals_empty),
                icon = { GoalsIcon(tint = PlannerColors.faint) },
            )
        }
    }
}

@Composable
private fun GoalCard(goal: PlannerItem, viewModel: ItemsViewModel, onClick: () -> Unit) {
    val progress by produceState<Float?>(initialValue = null, goal.id) {
        value = viewModel.progressOf(goal.id)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PlannerColors.surface)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 15.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = goal.done,
            onCheckedChange = { viewModel.setDone(goal, it) },
        )
        Column(Modifier
            .weight(1f)
            .padding(vertical = 8.dp)) {
            Text(
                goal.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                textDecoration = if (goal.done) TextDecoration.LineThrough else null,
                color = animateDoneColor(isDone = goal.done, base = PlannerColors.text).value,
            )
            goal.notes?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerColors.mutedLight,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            progress?.let { p ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 11.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(PlannerColors.chipBg),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth(p.coerceIn(0f, 1f))
                            .height(5.dp)
                            .background(PlannerColors.primary),
                    ) {}
                }
            }
            CardMetaRow(goal)
        }
    }
}

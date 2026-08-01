package com.app.ai.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.ai.planner.R
import com.app.ai.planner.ui.theme.PlannerColors

/** All entries of a (Claude-assigned) category, across all types. */
@Composable
fun CategoryScreen(
    name: String,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    viewModel: ItemsViewModel = viewModel(),
) {
    val items by viewModel.byCategory(name).collectAsState(initial = emptyList())

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp, 14.dp, 20.dp, 24.dp),
    ) {
        BackHeader(onBack = onBack)
        Text(
            name,
            style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic),
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            stringResource(R.string.category_subtitle, items.size),
            style = MaterialTheme.typography.bodySmall,
            color = PlannerColors.muted,
        )

        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlannerColors.surface)
                        .clickable { onItemClick(item.id) }
                        .padding(15.dp, 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeIcon(item.type, tint = PlannerColors.muted)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                            ),
                            color = if (item.done) PlannerColors.faint else androidx.compose.ui.graphics.Color.Unspecified,
                        )
                        item.notes?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PlannerColors.mutedLight,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        CardMetaRow(item, showCategory = false)
                    }
                }
            }
            if (items.isEmpty()) Text(
                stringResource(R.string.category_empty),
                color = PlannerColors.faint
            )
        }
    }
}

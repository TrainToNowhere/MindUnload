package com.app.mindunload.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.mindunload.R
import com.app.mindunload.ui.theme.PlannerColors

@Composable
fun KnowledgeScreen(
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    viewModel: WikiViewModel = viewModel(),
) {
    val notes by viewModel.notes.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp, 14.dp, 20.dp, 24.dp),
    ) {
        BackHeader(title = stringResource(R.string.drawer_knowledge), onBack = onBack)
        Text(
            stringResource(R.string.knowledge_subtitle, notes.size),
            style = MaterialTheme.typography.bodySmall,
            color = PlannerColors.muted,
        )

        // Card = title plus the shared meta line; the content itself lives on the detail
        // screen, which a tap opens — as with ideas/goals.
        Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { note ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlannerColors.surface)
                        .clickable { onItemClick(note.id) }
                        .padding(15.dp, 13.dp),
                ) {
                    Text(
                        note.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                    CardMetaRow(note)
                }
            }
            if (notes.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.knowledge_empty),
                    icon = { KnowledgeIcon(tint = PlannerColors.faint) },
                )
            }
        }
    }
}

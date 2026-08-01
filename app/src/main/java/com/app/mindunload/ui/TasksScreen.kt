package com.app.mindunload.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.mindunload.R
import com.app.mindunload.data.ItemType
import com.app.mindunload.data.Priority

@Composable
fun TasksScreen(
    onOpenDrawer: () -> Unit,
    onItemClick: (Long) -> Unit,
    viewModel: ItemsViewModel = viewModel(),
) {
    val tasks by viewModel.items(ItemType.TASK).collectAsState(initial = emptyList())
    val groups = listOf(
        R.string.priority_group_high to tasks.filter { it.priority == Priority.HIGH },
        R.string.priority_group_medium to tasks.filter { it.priority == Priority.MEDIUM },
        R.string.priority_group_low to tasks.filter { it.priority == Priority.LOW || it.priority == Priority.NONE },
    ).filter { it.second.isNotEmpty() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.tab_tasks),
                subtitle = stringResource(R.string.tasks_subtitle, tasks.count { !it.done }),
                onOpenDrawer = onOpenDrawer,
            )
        }
        groups.forEach { (labelRes, items) ->
            item {
                Column(Modifier.padding(top = 12.dp)) { SectionLabel(stringResource(labelRes)) }
            }
            items(items, key = { it.id }) { task ->
                ItemRow(
                    item = task,
                    showListName = false,
                    onClick = { onItemClick(task.id) },
                    onToggleDone = { viewModel.setDone(task, it) },
                )
            }
        }
        if (tasks.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.list_empty),
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

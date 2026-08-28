package com.app.mindunload.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.mindunload.R
import com.app.mindunload.data.ItemType
import com.app.mindunload.data.PlannerItem
import com.app.mindunload.ui.theme.PlannerColors

@Composable
fun ShoppingScreen(onOpenDrawer: () -> Unit, viewModel: ItemsViewModel = viewModel()) {
    val allItems by viewModel.items(ItemType.SHOPPING_ITEM).collectAsState(initial = emptyList())
    val showDone by viewModel.showDone.collectAsState()
    val defaultListName = stringResource(R.string.shopping_default_list)
    val lists = allItems.groupBy { it.listName ?: defaultListName }
    val openCount = allItems.count { !it.done }
    // Collapsed by default — the tab opens compact, same as the appointments weeks.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 8.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.tab_shopping),
                subtitle = stringResource(R.string.shopping_subtitle, lists.size, openCount),
                onOpenDrawer = onOpenDrawer,
            )
        }
        item(key = "show-done") {
            ShowDoneFilter(
                checked = showDone,
                onCheckedChange = viewModel::setShowDone,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        lists.forEach { (listName, items) ->
            val isExpanded = expanded[listName] == true
            val listOpenCount = items.count { !it.done }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PlannerColors.surface),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClickLabel = stringResource(
                                    if (isExpanded) R.string.shopping_list_collapse
                                    else R.string.shopping_list_expand
                                ),
                                onClick = { expanded[listName] = !isExpanded },
                            )
                            .padding(14.dp, 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel(listName)
                        Spacer(Modifier.weight(1f))
                        if (!isExpanded) {
                            Text(
                                pluralStringResource(
                                    R.plurals.shopping_list_open_count,
                                    listOpenCount,
                                    listOpenCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (listOpenCount == 0) PlannerColors.faint else PlannerColors.muted,
                            )
                        }
                        BackChevronIcon(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .rotate(if (isExpanded) 90f else 270f),
                            tint = PlannerColors.mutedLight,
                        )
                    }
                    AnimatedVisibility(visible = isExpanded) {
                        Column {
                            HorizontalDivider(color = PlannerColors.divider)
                            items.forEachIndexed { index, shopItem ->
                                ShoppingRow(shopItem, onToggle = { viewModel.setDone(shopItem, it) })
                                if (index != items.lastIndex) HorizontalDivider(color = PlannerColors.divider)
                            }
                        }
                    }
                }
            }
        }
        if (allItems.isEmpty()) {
            item {
                EmptyState(
                    message = stringResource(R.string.shopping_empty),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ShoppingRow(item: PlannerItem, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(14.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.done, onCheckedChange = onToggle)
        Text(
            item.title,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            color = animateDoneColor(isDone = item.done, base = PlannerColors.text).value,
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp),
        )
        item.quantity?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = PlannerColors.mutedLight)
        }
    }
}

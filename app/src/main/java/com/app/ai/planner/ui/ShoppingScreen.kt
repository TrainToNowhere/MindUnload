package com.app.ai.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.ai.planner.R
import com.app.ai.planner.data.ItemType
import com.app.ai.planner.data.PlannerItem
import com.app.ai.planner.ui.theme.PlannerColors

@Composable
fun ShoppingScreen(onOpenDrawer: () -> Unit, viewModel: ItemsViewModel = viewModel()) {
    val allItems by viewModel.items(ItemType.SHOPPING_ITEM).collectAsState(initial = emptyList())
    val defaultListName = stringResource(R.string.shopping_default_list)
    val lists = allItems.groupBy { it.listName ?: defaultListName }
    val openCount = allItems.count { !it.done }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 18.dp,
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
        lists.forEach { (listName, items) ->
            item {
                Column(Modifier.padding(top = 12.dp)) { SectionLabel(listName) }
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PlannerColors.surface),
                ) {
                    items.forEachIndexed { index, shopItem ->
                        ShoppingRow(shopItem, onToggle = { viewModel.setDone(shopItem, it) })
                        if (index != items.lastIndex) HorizontalDivider(color = PlannerColors.divider)
                    }
                }
            }
        }
        if (allItems.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.shopping_empty),
                    modifier = Modifier.padding(top = 24.dp)
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
            color = if (item.done) PlannerColors.faint else PlannerColors.text,
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp),
        )
        item.quantity?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = PlannerColors.mutedLight)
        }
    }
}

package com.app.mindunload.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.mindunload.R
import com.app.mindunload.ui.theme.PlannerColors
import com.app.mindunload.ui.theme.Radius
import com.app.mindunload.ui.theme.Spacing

/**
 * Navigation drawer content. Lives in its own file because it carries three layers of
 * state (counts from the [DrawerViewModel], a scroll list, the settings entry pinned at
 * the bottom) that have no business in the navigation host.
 *
 * Width: 280 dp — the M3 standard drawer width, deliberately not migrated to a
 * personal-spacing token because the value comes from a Material guideline.
 */
@Composable
internal fun DrawerContent(viewModel: DrawerViewModel, onNavigate: (String) -> Unit) {
    val noteCount by viewModel.noteCount.collectAsState()
    val ideaCount by viewModel.ideaCount.collectAsState()
    val goalCount by viewModel.goalCount.collectAsState()
    val categories by viewModel.categories.collectAsState()

    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = Color(0xFFFBFAF7),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Drawer title block: top needs 22 dp to clear the status bar inset. Migrated
            // to [Spacing.xl] horizontally. Title only — no subtitle underneath.
            Column(Modifier.padding(Spacing.xl, 22.dp, Spacing.xl, Spacing.l)) {
                Text(
                    stringResource(R.string.drawer_title),
                    // Theme sets titleLarge to Normal serif; any extra Italic here
                    // would re-apply the very thing the theme was tuned to avoid.
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.m),
            ) {
                SectionLabel(stringResource(R.string.drawer_collections))
                DrawerItem(
                    stringResource(R.string.drawer_knowledge),
                    noteCount.toString(),
                    icon = { KnowledgeIcon(tint = PlannerColors.muted) },
                ) { onNavigate("wiki") }
                DrawerItem(
                    stringResource(R.string.drawer_ideas),
                    ideaCount.toString(),
                    icon = { IdeasIcon(tint = PlannerColors.muted) },
                ) { onNavigate("ideas") }
                DrawerItem(
                    stringResource(R.string.drawer_goals),
                    goalCount.toString(),
                    icon = { GoalsIcon(tint = PlannerColors.muted) },
                ) { onNavigate("goals") }
                SectionLabel(stringResource(R.string.drawer_functions))
                DrawerItem(
                    stringResource(R.string.usage_title),
                    null,
                    icon = { UsageIcon(tint = PlannerColors.muted) },
                ) { onNavigate("usage") }
                DrawerItem(
                    stringResource(R.string.stats_title),
                    null,
                    icon = { StatsIcon(tint = PlannerColors.muted) },
                ) { onNavigate("stats") }
                SectionLabel(stringResource(R.string.drawer_categories))
                categories.forEach { cat ->
                    DrawerItem(
                        cat.name,
                        cat.count.toString(),
                        icon = { CategoryIcon(cat.name, cat.topType, tint = PlannerColors.muted) },
                    ) { onNavigate("category/${android.net.Uri.encode(cat.name)}") }
                }
            }
            HorizontalDivider(color = PlannerColors.divider)
            // navigationBarsPadding: with the 3-button system nav bar shown, the drawer
            // sheet would otherwise extend the Settings row underneath it — same fix as
            // the bottom tab bar in [BottomNav].
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(Spacing.m),
            ) {
                DrawerItem(
                    stringResource(R.string.settings_headline),
                    null,
                    icon = { SettingsIcon(tint = PlannerColors.muted) },
                ) { onNavigate("settings") }
            }
        }
    }
}

@Composable
internal fun DrawerItem(
    label: String,
    count: String?,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Outer 2 dp keeps consecutive rows visually grouped; the inner
            // spacing is the per-row tap-density (see comment above).
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
            // Was 11 dp/10 dp, the original drawer-specific tap density.
            // Now on the [Spacing.sm] scale; 2 dp + 8 dp stacks to the same
            // visual rhythm the drawer established.
            .padding(vertical = Spacing.sm, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            it()
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        count?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = PlannerColors.faint
            )
        }
    }
}

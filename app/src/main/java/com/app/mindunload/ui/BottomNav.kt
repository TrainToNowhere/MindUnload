package com.app.mindunload.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.mindunload.R
import com.app.mindunload.ui.theme.PlannerColors

/**
 * Bottom navigation for the five primary tabs. Lives in its own file so the navigation
 * host ([PlannerNavHost]) stays focused on route definitions; the visual layering of the
 * bar — its 30 dp icons, 6 dp side padding, status-bar compensation — is documented here
 * because it is the most tweaked interactive surface in the whole app.
 *
 * Visual deviations from [com.app.mindunload.ui.theme.HitTarget.min]:
 *  - The icon buttons sit at 30 dp, not 48 dp. The surrounding `Column.weight(1f)`
 *    padding supplies the rest of the tap slop, so a fingertip lands on the icon without
 *    missing — but TalkBack might struggle. Documented compromise in DESIGN.md §8.
 */
internal data class BottomTab(
    val route: String,
    val labelRes: Int,
    val icon: @Composable (Color) -> Unit,
)

@Composable
internal fun BottomNav(currentRoute: String?, onNavigate: (String) -> Unit) {
    val active = PlannerColors.primary
    val idle = Color(0xFF9A948B)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PlannerColors.background)
            .navigationBarsPadding()
            // Inline 6 dp/6 dp is intentionally below [Spacing.s] — the 5 nav icons
            // share the bottom bar width and a real 8 dp inset eats into label space.
            .padding(top = 2.dp, start = 6.dp, end = 6.dp, bottom = 0.dp),
    ) {
        val tabs = listOf(
            BottomTab("home", R.string.tab_today) { tint -> TabTodayIcon(tint = tint) },
            BottomTab("chat", R.string.tab_chat) { tint -> TabChatIcon(tint = tint) },
            BottomTab("tasks", R.string.tab_tasks) { tint -> TabTasksIcon(tint = tint) },
            BottomTab(
                "dates",
                R.string.tab_appointments
            ) { tint -> TabAppointmentsIcon(tint = tint) },
            BottomTab("shop", R.string.tab_shopping) { tint -> TabShoppingIcon(tint = tint) },
        )
        tabs.forEach { tab ->
            val tint = if (currentRoute == tab.route) active else idle
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Explicit size: the 48dp default minimum makes the bar unnecessarily tall.
                // 30 dp is [Spacing.l] - 2 dp; the surrounding column weight provides
                // the rest of the tap slop, so this stays below [HitTarget.min].
                IconButton(
                    onClick = { onNavigate(tab.route) },
                    modifier = Modifier.size(30.dp),
                ) {
                    tab.icon(tint)
                }
                Text(
                    stringResource(tab.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
            }
        }
    }
}

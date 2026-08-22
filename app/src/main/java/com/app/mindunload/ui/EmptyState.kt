package com.app.mindunload.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.mindunload.ui.theme.PlannerColors
import com.app.mindunload.ui.theme.Spacing

/**
 * The "nothing here yet" placeholder used by every list screen. Has to read as a *friendly
 * resting state*, not a missing-data error — an app where users write their first thought
 * should not bark at them with technical language when a list is still empty.
 *
 * Nullable [icon] lets callers reuse an existing line icon (e.g. the section's tab icon)
 * at the same 21 dp size, so the placeholder reads as belonging to the list rather than
 * appearing out of nowhere. Pass null to omit.
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 320.dp)
            .padding(top = Spacing.l, bottom = Spacing.l)
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            // Tinted faint: the placeholder should be visible, but quieter than the
            // actively-tinted icons in the rest of the screen.
            icon()
            Spacer(Modifier.size(Spacing.s))
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = PlannerColors.muted,
        )
    }
}

/**
 * Compact counterpart to [EmptyState] for *sub-section* hints where a full centred
 * empty state would dominate (e.g. "no appointments today" under a section label).
 * Left-aligned one-line metadata; same colour as the original [PlannerColors.faint]
 * hint it replaces, no visual change beyond living in one composable.
 */
@Composable
fun SmallEmptyHint(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = PlannerColors.faint,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.s, start = 2.dp),
    )
}

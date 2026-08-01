package com.app.ai.planner.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Short confirmation for actions whose effect is invisible on screen (saving a key,
 * cleanup, export/import, promoting to the wiki). Without it a tap looks like nothing
 * happened at all.
 */
class Feedback(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    /** [long] for error messages, which need more reading time than a confirmation. */
    fun show(message: String, long: Boolean = false) {
        scope.launch {
            // A new message replaces the current one instead of queueing behind it.
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(
                message,
                duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }
}

val LocalFeedback: ProvidableCompositionLocal<Feedback> = compositionLocalOf {
    error("No Feedback provided — wrap the content in CompositionLocalProvider(LocalFeedback ...)")
}

@Composable
fun rememberFeedback(): Feedback {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    return remember(hostState, scope) { Feedback(hostState, scope) }
}

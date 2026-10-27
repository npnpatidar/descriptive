package com.paperpractice.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * A compact drag handle. Every threshold moves one position, so the list does
 * not need a floating drag overlay, and the same gesture remains usable with
 * screen readers via explicit move-up/move-down menu items.
 */
@Composable
fun ReorderHandle(
    contentDescription: String,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val latestOnMove by rememberUpdatedState(onMove)
    val threshold = with(LocalDensity.current) { 30.dp.toPx() }
    var accumulated by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.DragHandle,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            },
            modifier = Modifier
                .size(24.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { accumulated = 0f },
                        onDragEnd = { accumulated = 0f },
                        onDragCancel = { accumulated = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount
                            while (accumulated >= threshold) {
                                accumulated -= threshold
                                latestOnMove(1)
                            }
                            while (accumulated <= -threshold) {
                                accumulated += threshold
                                latestOnMove(-1)
                            }
                        }
                    )
                }
        )
    }
}

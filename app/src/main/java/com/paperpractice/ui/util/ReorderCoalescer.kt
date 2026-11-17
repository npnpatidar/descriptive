package com.paperpractice.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.channels.Channel

/**
 * Returns a stable callback that queues reorder steps and applies them one at a
 * time. A single drag can cross several thresholds in quick succession; the
 * collector applies those steps in order, so the underlying (suspend) writes
 * never overlap. Call this once per item so each item has its own queue.
 */
@Composable
fun rememberSequentialReorder(apply: suspend (Int) -> Unit): (Int) -> Unit {
    val latestApply by rememberUpdatedState(apply)
    val channel = remember { Channel<Int>(Channel.UNLIMITED) }

    LaunchedEffect(channel) {
        try {
            for (delta in channel) {
                latestApply(delta)
            }
        } finally {
            // LaunchedEffect is cancelled when the item leaves composition.
            channel.close()
        }
    }

    val emit: (Int) -> Unit = remember(channel) { { delta -> channel.trySend(delta) } }
    return emit
}

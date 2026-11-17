package com.paperpractice.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.paperpractice.domain.ImageContent
import com.paperpractice.ui.util.decodeImageForDisplay

/** Read-only image gallery used by the questions list and practice screen. */
@Composable
fun AttachedImages(
    images: List<ImageContent>,
    modifier: Modifier = Modifier,
    description: String = "Attached image"
) {
    if (images.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        images.forEachIndexed { index, image ->
            // ByteArray has referential equality: keying on `image.bytes`
            // re-decodes on every recomposition that rebuilds the content
            // model (for example after a refresh reads the BLOB again). The
            // image id is stable, but a replaced image may reuse its id with
            // new bytes, so include a cheap content token as well.
            val contentToken = imageContentToken(image.bytes)
            val bitmap = remember(image.id, contentToken) {
                decodeImageForDisplay(image.bytes)
                    ?.asImageBitmap()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "$description ${index + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

/**
 * A cheap fingerprint of an encoded image: its size plus a small rolling hash
 * over the first, middle and last bytes. Replacing an image under the same id
 * changes at least one of these in practice, so the thumbnail is re-decoded
 * only when the bytes actually change rather than on every reconnect.
 */
private fun imageContentToken(bytes: ByteArray): String {
    if (bytes.isEmpty()) return "0:0"
    var hash = 17
    hash = 31 * hash + bytes.size
    hash = 31 * hash + bytes[0].toInt()
    hash = 31 * hash + bytes[bytes.size / 2].toInt()
    hash = 31 * hash + bytes[bytes.lastIndex].toInt()
    return "${bytes.size}:$hash"
}

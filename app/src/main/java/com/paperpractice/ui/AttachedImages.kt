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
            val bitmap = remember(image.id, image.bytes) {
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

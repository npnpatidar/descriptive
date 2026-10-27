package com.paperpractice.ui.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max

/**
 * Decodes a downsampled bitmap for on-screen previews. Stored images are
 * already compressed, but decoding full-size bitmaps for small thumbnails or
 * a list of questions can still use a surprising amount of memory.
 */
fun decodeImageForDisplay(bytes: ByteArray, maxDimension: Int = 700): Bitmap? {
    if (bytes.isEmpty() || maxDimension <= 0) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxDimension) {
        sampleSize *= 2
    }

    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize }
    )
}

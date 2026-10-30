/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Orchard.
 *
 * Orchard is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Orchard is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Orchard. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.stuffbyren.pomelo.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import xyz.stuffbyren.pomelo.artwork.highResolutionArtworkUrl
import xyz.stuffbyren.pomelo.ui.theme.CanopyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Graded covers are a few kilobytes each and every player surface asks for the same one, so a
 * small access-ordered cache makes returning to a cover free.
 */
private val blurredCovers = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > 16
}

@Synchronized
internal fun cachedBlurredCover(artworkUrl: String): ImageBitmap? =
    if (artworkUrl.isBlank()) null else blurredCovers[artworkUrl]

@Synchronized
private fun cacheBlurredCover(artworkUrl: String, image: ImageBitmap) {
    blurredCovers[artworkUrl] = image
}

internal suspend fun bakeBlurredCover(context: Context, artworkUrl: String): ImageBitmap? {
    val source = loadSmallCover(context, artworkUrl) ?: return null
    return withContext(Dispatchers.Default) { runCatching { gradeBlurSource(source) }.getOrNull() }
        ?.also { cacheBlurredCover(artworkUrl, it) }
}

/** Same 128 px URL the palette sampler decodes, so this is usually a memory-cache hit. */
private suspend fun loadSmallCover(context: Context, artworkUrl: String): Bitmap? {
    val loader = SingletonImageLoader.get(context)
    suspend fun load(url: String): Bitmap? = loader.execute(
        ImageRequest.Builder(context)
            .data(url)
            .size(SOURCE_SIZE)
            .allowHardware(false)
            .build(),
    ).image?.toBitmap()

    val sized = highResolutionArtworkUrl(artworkUrl, SOURCE_SIZE)
    return load(sized) ?: if (sized != artworkUrl) load(artworkUrl) else null
}

/**
 * Shrinks [source] to a few dozen pixels and grades it for the blurred backdrop. The blur itself
 * runs on the GPU, so this only has to be small and already toned.
 */
internal fun gradeBlurSource(source: Bitmap): ImageBitmap {
    val width = minOf(source.width, MAX_WIDTH).coerceAtLeast(1)
    val height = (width * source.height / source.width.coerceAtLeast(1)).coerceIn(1, width * 4)
    val graded = createBitmap(width, height)
    Canvas(graded).apply {
        // Transparent covers would otherwise blur toward black at their edges.
        drawColor(CanopyColors.PlayerBackdrop.toArgb())
        drawBitmap(
            source,
            null,
            Rect(0, 0, width, height),
            Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(grade()) },
        )
    }
    val pixels = IntArray(width * height)
    graded.getPixels(pixels, 0, width, 0, 0, width, height)
    capLuma(pixels)
    graded.setPixels(pixels, 0, width, 0, 0, width, height)
    graded.prepareToDraw()
    return graded.asImageBitmap()
}

/** Saturation lift, then a pull toward the chrome tone, as the design's backdrop does. */
private fun grade(): ColorMatrix {
    val chrome = CanopyColors.Chrome
    val keep = 1f - DIM
    return ColorMatrix().apply {
        setSaturation(SATURATION)
        postConcat(
            ColorMatrix(
                floatArrayOf(
                    keep, 0f, 0f, 0f, DIM * chrome.red * 255f,
                    0f, keep, 0f, 0f, DIM * chrome.green * 255f,
                    0f, 0f, keep, 0f, DIM * chrome.blue * 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
}

/** White controls sit straight on this, so bright covers are darkened until they stay legible. */
private fun capLuma(pixels: IntArray) {
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        val luma = (red * 299 + green * 587 + blue * 114) / 1000
        if (luma <= MAX_LUMA) continue
        val k = MAX_LUMA.toFloat() / luma
        pixels[i] = (0xFF shl 24) or
            ((red * k).toInt() shl 16) or
            ((green * k).toInt() shl 8) or
            (blue * k).toInt()
    }
}

private const val SOURCE_SIZE = 128

/** Heavier blur follows on the GPU, so nothing a larger source would resolve survives it. */
private const val MAX_WIDTH = 72
private const val SATURATION = 1.3f
private const val DIM = 0.3f

/** sRGB luma ceiling that keeps white text above roughly 4.5:1. */
private const val MAX_LUMA = 118

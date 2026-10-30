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

import android.graphics.Bitmap
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The cover, or the motion art's live frames, as the player's backdrop, blurred well past
 * recognition so the space around the controls carries the art's real colours.
 *
 * Sources are tiny graded bitmaps (see [gradeBlurSource]). They are drawn into [layer] at a
 * fraction of screen resolution and blurred there by the GPU, then stretched to fill. The
 * platform caches the blurred layer, so a still backdrop costs one texture draw per frame.
 */
@Stable
class BlurredArtwork internal constructor(
    internal val layer: GraphicsLayer,
    internal val fadeLayer: GraphicsLayer,
) {
    internal var under by mutableStateOf<ImageBitmap?>(null)
    internal var over by mutableStateOf<ImageBitmap?>(null)
    internal var fade by mutableFloatStateOf(1f)
    internal var incoming by mutableStateOf<ImageBitmap?>(null)
    internal var incomingAlpha by mutableFloatStateOf(0f)

    internal val requests = MutableStateFlow<BlurRequest?>(null)
    internal val rawFrames = MutableStateFlow<Bitmap?>(null)

    /** Set while drawing the backdrop; the fade over the art reuses them to line up exactly. */
    internal var area = IntSize.Zero
    internal var layerSize = IntSize.Zero
    private var blurRadius = Float.NaN

    val isReady: Boolean get() = over != null || under != null

    /** A small frame of the motion art as drawn; frames blend into each other as they arrive. */
    fun pushFrame(frame: Bitmap) {
        rawFrames.value = frame
    }

    internal suspend fun show(request: BlurRequest) {
        val image = request.image
        if (image === over) return
        // An interrupted fade keeps whichever image was more visible underneath.
        under = if (fade >= 0.5f) over else under
        over = image
        val spec = request.spec
        if (spec == null) {
            fade = 1f
            under = null
            return
        }
        fade = 0f
        // A handoff may replace [over] mid-fade; this animation then no longer owns the alpha.
        animate(0f, 1f, animationSpec = spec) { value, _ -> if (over === image) fade = value }
        if (over === image) under = null
    }

    internal fun updateBlur(radius: Float) {
        if (radius == blurRadius) return
        blurRadius = radius
        layer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
    }
}

internal class BlurRequest(val image: ImageBitmap?, val spec: FiniteAnimationSpec<Float>?)

/**
 * Crossfades between covers as [artworkUrl] changes, follows frames handed to
 * [BlurredArtwork.pushFrame] while [videoUrl] plays, and blends in [incomingArtworkUrl] by
 * [incomingProgress] while a mix runs.
 */
@Composable
fun rememberBlurredArtwork(
    artworkUrl: String,
    videoUrl: String = "",
    incomingArtworkUrl: String = "",
    incomingProgress: Float = 0f,
): BlurredArtwork {
    val graphics = LocalGraphicsContext.current
    val art = remember(graphics) {
        BlurredArtwork(
            graphics.createGraphicsLayer(),
            graphics.createGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen },
        )
    }
    DisposableEffect(art) {
        onDispose {
            graphics.releaseGraphicsLayer(art.layer)
            graphics.releaseGraphicsLayer(art.fadeLayer)
        }
    }

    val target = rememberBlurredCover(artworkUrl)
    val incoming = rememberBlurredCover(incomingArtworkUrl)
    SideEffect {
        // A mix that reached its handoff already shows the new cover; fading it in again would
        // flash the old one for a frame.
        if (target != null && target !== art.over && target === art.incoming && art.incomingAlpha >= 0.5f) {
            art.over = target
            art.under = null
            art.fade = 1f
            art.requests.value = BlurRequest(target, null)
        }
        art.incoming = incoming
        art.incomingAlpha = incomingProgress.coerceIn(0f, 1f)
    }
    LaunchedEffect(art) {
        art.requests.filterNotNull().collectLatest { art.show(it) }
    }
    LaunchedEffect(target, artworkUrl.isBlank(), videoUrl) {
        // The last image stays up while the next cover bakes.
        if (target != null || artworkUrl.isBlank()) art.requests.value = BlurRequest(target, tween(COVER_FADE_MS))
    }
    LaunchedEffect(art, videoUrl) {
        art.rawFrames.value = null
        if (videoUrl.isBlank()) return@LaunchedEffect
        art.rawFrames.filterNotNull().collectLatest { raw ->
            val graded = withContext(Dispatchers.Default) { runCatching { gradeBlurSource(raw) }.getOrNull() }
            // Linear and as long as the gap between frames, so the motion reads as continuous.
            if (graded != null) art.requests.value = BlurRequest(graded, tween(FRAME_BLEND_MS, easing = LinearEasing))
        }
    }
    return art
}

@Composable
private fun rememberBlurredCover(artworkUrl: String): ImageBitmap? {
    val context = LocalContext.current
    var cover by remember(artworkUrl) { mutableStateOf(cachedBlurredCover(artworkUrl)) }
    LaunchedEffect(artworkUrl) {
        if (cover == null && artworkUrl.isNotBlank()) cover = bakeBlurredCover(context, artworkUrl)
    }
    return cover
}

/** Fills the node with the blurred art, cropped the way ContentScale.Crop would. */
fun Modifier.blurredArtworkBackdrop(art: BlurredArtwork): Modifier = drawBehind {
    if (size.width < 1f || size.height < 1f) return@drawBehind
    val small = IntSize(
        ceil(size.width / DOWNSCALE).toInt().coerceAtLeast(1),
        ceil(size.height / DOWNSCALE).toInt().coerceAtLeast(1),
    )
    art.area = IntSize(size.width.roundToInt(), size.height.roundToInt())
    art.layerSize = small
    art.updateBlur(blurRadius(BLUR_SIGMA.toPx() / DOWNSCALE))
    art.layer.record(small) {
        art.under?.let { drawCropped(it, 1f) }
        art.over?.let { drawCropped(it, art.fade) }
        art.incoming?.let { drawCropped(it, art.incomingAlpha) }
    }
    drawStretched(art)
}

/**
 * Fades the blurred art in over this node's content between [startFraction] and [endFraction]
 * of its height, so art drawn above the backdrop melts into it. The node must sit inside the
 * backdrop at its top-left corner; [shiftX] cancels a horizontal translation applied to it.
 */
fun Modifier.blurredArtworkFade(
    art: BlurredArtwork,
    startFraction: Float,
    endFraction: Float,
    shiftX: () -> Float,
): Modifier = drawWithCache {
    val mask = smoothScrimBrush(Color.Transparent, Color.Black, startFraction, endFraction)
    onDrawWithContent {
        drawContent()
        art.fadeLayer.record {
            translate(left = -shiftX()) { drawStretched(art) }
            drawRect(mask, blendMode = BlendMode.DstIn)
        }
        drawLayer(art.fadeLayer)
    }
}

private fun DrawScope.drawStretched(art: BlurredArtwork) {
    val small = art.layerSize
    if (small.width <= 0 || small.height <= 0) return
    scale(
        scaleX = art.area.width / small.width.toFloat(),
        scaleY = art.area.height / small.height.toFloat(),
        pivot = Offset.Zero,
    ) { drawLayer(art.layer) }
}

private fun DrawScope.drawCropped(image: ImageBitmap, alpha: Float) {
    if (alpha <= 0f) return
    val scale = max(size.width / image.width, size.height / image.height)
    val visible = IntSize(
        (size.width / scale).roundToInt().coerceIn(1, image.width),
        (size.height / scale).roundToInt().coerceIn(1, image.height),
    )
    drawImage(
        image,
        srcOffset = IntOffset((image.width - visible.width) / 2, (image.height - visible.height) / 2),
        srcSize = visible,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
    )
}

/** The platform turns a blur radius into sigma as 0.57735 * r + 0.5; this inverts it. */
private fun blurRadius(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(1f)

/** Blur runs at this fraction of screen resolution; the result has no detail to lose. */
private const val DOWNSCALE = 6f

/** Standard deviation of the blur at screen scale, as in the design. */
private val BLUR_SIGMA = 56.dp

private const val COVER_FADE_MS = 600

/** Matches the video view's frame interval for the backdrop and glass. */
private const val FRAME_BLEND_MS = 150

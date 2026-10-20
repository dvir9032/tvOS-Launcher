package com.shelf.launcher.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * A blurred picture of the home screen, used behind the glass of menus.
 *
 * Live blur of what's under a window needs Android 12. On older TVs Shelf does
 * what blur libraries do there: take a snapshot of the screen, blur a small copy
 * of it off the main thread, and draw the matching part inside each glass panel.
 * The snapshot is retaken whenever the screen behind changes.
 */
class Frost(val image: ImageBitmap, val screen: IntSize)

/** Snapshot of [layer] (the whole home screen), blurred. Null if the TV can't do it. */
suspend fun captureFrost(layer: GraphicsLayer): Frost? = runCatching {
    val snapshot = layer.toImageBitmap()
    val screen = IntSize(snapshot.width, snapshot.height)
    withContext(Dispatchers.Default) {
        val full = snapshot.asAndroidBitmap().let {
            if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
        }
        // Blur at 1/10 size: fast, and scaling it back up smooths it further.
        val w = (full.width / 10).coerceAtLeast(1)
        val h = (full.height / 10).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(full, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        boxBlur(small, radius = 4, passes = 3)
        Frost(small.asImageBitmap(), screen)
    }
}.getOrNull()

/** Three box-blur passes look like a gaussian blur. */
private fun boxBlur(bitmap: Bitmap, radius: Int, passes: Int) {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    val temp = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    repeat(passes) {
        blurLine(pixels, temp, w, h, radius, horizontal = true)
        blurLine(temp, pixels, w, h, radius, horizontal = false)
    }
    bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
}

private fun blurLine(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val length = if (horizontal) w else h
    val div = 2 * r + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val c = i.coerceIn(0, length - 1)
            return if (horizontal) line * w + c else c * w + line
        }
        var a = 0
        var red = 0
        var green = 0
        var blue = 0
        for (i in -r..r) {
            val p = src[at(i)]
            a += p ushr 24
            red += (p shr 16) and 0xFF
            green += (p shr 8) and 0xFF
            blue += p and 0xFF
        }
        for (i in 0 until length) {
            dst[at(i)] = ((a / div) shl 24) or ((red / div) shl 16) or ((green / div) shl 8) or (blue / div)
            val out = src[at(i - r)]
            val inn = src[at(i + r + 1)]
            a += (inn ushr 24) - (out ushr 24)
            red += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
            green += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
            blue += (inn and 0xFF) - (out and 0xFF)
        }
    }
}

/**
 * Frosted glass: shows the blurred screen behind this element, lined up with
 * where the element sits, darkened a little so white text stays readable.
 * Until the blur is ready (or if it fails) it shows [fallback].
 */
fun Modifier.frostedGlass(
    frost: Frost?,
    shape: Shape,
    fallback: Brush,
    dim: Float = 0.3f,
): Modifier = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val reveal by animateFloatAsState(if (frost != null) 1f else 0f, tween(220), label = "frost")
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .shadow(28.dp, shape, clip = false)
        .clip(shape)
        .background(fallback)
        .drawBehind {
            if (frost != null && reveal > 0f) {
                drawImage(
                    image = frost.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(frost.image.width, frost.image.height),
                    dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()),
                    dstSize = frost.screen,
                    alpha = reveal,
                    filterQuality = FilterQuality.Low, // bilinear: smooth when scaled up
                )
                drawRect(Color.Black.copy(alpha = dim * reveal))
                drawRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f * reveal), Color.Transparent))
                )
            }
        }
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.06f))),
            shape = shape,
        )
}

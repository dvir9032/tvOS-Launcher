package com.shelf.launcher.ui

import android.text.format.DateFormat
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.R
import com.shelf.launcher.data.BackdropStyle
import com.shelf.launcher.data.ClockMode
import com.shelf.launcher.data.TvApp
import com.shelf.launcher.data.TvSource
import com.shelf.launcher.update.UpdateState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CondensedBold = FontFamily(
    Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
)
private val Ink = Color(0xFF0B0E13)

enum class MoveDir { LEFT, RIGHT, UP, DOWN }

/** Full-screen artwork that cross-fades whenever focus moves to another app. */
@Composable
fun Backdrop(app: TvApp?, style: BackdropStyle, modifier: Modifier = Modifier) {
    // Fade by app key, so reloading the app list doesn't restart the fade.
    val byKey = remember { mutableMapOf<String, TvApp>() }
    app?.let { byKey[it.key] = it }
    Crossfade(
        targetState = app?.key,
        modifier = modifier,
        animationSpec = tween(450),
        label = "backdrop",
    ) { key ->
        val current = key?.let { byKey[it] }
        Box(Modifier.fillMaxSize()) {
            val top = current?.tint ?: Color(0xFF25305E)
            val bottom = current?.tintDark ?: Color(0xFF0B1530)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(top, bottom, Color.Black),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        )
                    )
            )
            when (style) {
                BackdropStyle.BLURRED -> current?.backdrop?.let { art ->
                    Image(
                        bitmap = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low, // bilinear stretch = soft blur
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = 0.9f
                                scaleX = 1.15f
                                scaleY = 1.15f
                            },
                    )
                }
                BackdropStyle.SHARP -> current?.banner?.let { art ->
                    Image(
                        bitmap = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 0.85f },
                    )
                }
                BackdropStyle.COLOR -> Unit
            }
            // Vignette
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                        )
                    )
            )
        }
    }
}

/** The focused item's name, big and condensed, in the top corner. */
@Composable
fun ShelfTitle(text: String, fontSize: Dp, modifier: Modifier = Modifier) {
    val size = with(LocalDensity.current) { fontSize.toSp() }
    BasicText(
        text = text.uppercase(),
        modifier = modifier,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = Color.White,
            fontFamily = CondensedBold,
            fontWeight = FontWeight.Bold,
            fontSize = size,
            lineHeight = size * 0.95f,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.45f),
                offset = Offset(0f, 6f),
                blurRadius = 30f,
            ),
        ),
    )
}

private fun formatTime(context: android.content.Context, mode: ClockMode): String {
    val now = Date()
    return when (mode) {
        ClockMode.H24 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        ClockMode.H12 -> SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
        ClockMode.SYSTEM -> DateFormat.getTimeFormat(context).format(now)
    }
}

/** Small frosted pill with the time and a profile dot. */
@Composable
fun ClockPill(mode: ClockMode, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember(mode) { mutableStateOf(formatTime(context, mode)) }
    LaunchedEffect(mode) {
        while (true) {
            now = formatTime(context, mode)
            val secondsLeft = 60 - Calendar.getInstance().get(Calendar.SECOND)
            delay(secondsLeft.coerceAtLeast(1) * 1000L)
        }
    }
    Row(
        modifier = modifier
            .height(36.dp)
            .background(Color.White.copy(alpha = 0.16f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .padding(start = 14.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            text = now,
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFF29E6B), Color(0xFFC2557A))))
                .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
        )
    }
}

/** Round frosted button for the top bar; turns white when focused. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, tween(150), label = "iconButton")
    Box(
        modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.16f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content(focused)
    }
}

@Composable
fun SettingsGlyph(focused: Boolean) {
    Image(
        painter = painterResource(R.drawable.ic_settings),
        contentDescription = "הגדרות",
        modifier = Modifier.size(20.dp),
        colorFilter = ColorFilter.tint(if (focused) Ink else Color.White),
    )
}

/** "Update available" pill. Only shown while there is something to say. */
@Composable
fun UpdatePill(state: UpdateState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = when (state) {
        is UpdateState.Available -> "עדכון זמין"
        is UpdateState.Downloading -> "מוריד ${(state.progress * 100).toInt()}%"
        UpdateState.Installing -> "מתקין…"
        else -> return
    }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(150), label = "updatePill")
    Box(
        modifier
            .height(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .background(if (focused) Color.White else Color(0xFF2F7BF6), CircleShape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = if (focused) Ink else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

/**
 * The frosted dock. There's no live blur on Android 10, but the backdrop is
 * already blurred, so a light translucent fill with a bright top edge reads as glass.
 */
@Composable
fun GlassTray(
    modifier: Modifier = Modifier,
    padH: Dp,
    padV: Dp,
    glassAlpha: Float,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 22)
    Box(
        modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = glassAlpha), shape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.10f))
                ),
                shape = shape,
            )
            .padding(horizontal = padH, vertical = padV)
    ) {
        content()
    }
}

/**
 * One app in the dock: grows, lifts and catches a glare when focused.
 * In move mode it pulses and the arrow keys move it instead of focus.
 */
@Composable
fun AppTile(
    app: TvApp,
    width: Dp,
    height: Dp,
    moving: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMove: (MoveDir) -> Unit,
    onMoveDone: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        when {
            moving -> 1.12f
            isFocused -> 1.10f
            else -> 1f
        },
        tween(180),
        label = "scale",
    )
    val glare by animateFloatAsState(if (isFocused) 0.38f else 0.10f, tween(180), label = "glare")
    val pulse = if (moving) {
        rememberInfiniteTransition(label = "move").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
            label = "pulse",
        ).value
    } else {
        0f
    }
    val shape = RoundedCornerShape(height * 0.19f)

    Box(
        modifier
            .zIndex(if (isFocused || moving) 1f else 0f)
            .size(width, height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = (if (isFocused) 28.dp else 4.dp).toPx()
                this.shape = shape
                clip = true
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                if (!moving) return@onPreviewKeyEvent false
                val code = event.nativeKeyEvent.keyCode
                val isDown = event.type == KeyEventType.KeyDown
                when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { if (isDown) onMove(MoveDir.LEFT); true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { if (isDown) onMove(MoveDir.RIGHT); true }
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> { if (isDown) onMove(MoveDir.UP); true }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { if (isDown) onMove(MoveDir.DOWN); true }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_BACK -> { if (!isDown) onMoveDone(); true }
                    else -> false
                }
            }
            .tvClickable(onLongClick = onLongClick, onClick = onClick)
            .background(Brush.verticalGradient(listOf(app.tint, app.tintDark)))
    ) {
        when {
            app.banner != null -> Image(
                bitmap = app.banner,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            app.icon != null -> Image(
                bitmap = app.icon,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize(0.55f)
                    .align(Alignment.Center),
            )
            else -> BasicText(
                text = app.label,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp),
                style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        // Glare + rim (a pulsing white rim while moving)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = glare),
                        0.45f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    )
                )
                .border(
                    width = if (moving) 4.dp else 1.dp,
                    color = Color.White.copy(alpha = if (moving) pulse else 0.25f),
                    shape = shape,
                )
        )
    }
}

/** An HDMI input: a slim frosted pill under the dock. */
@Composable
fun SourceTile(
    source: TvSource,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, tween(180), label = "sourceScale")
    val shape = RoundedCornerShape(height * 0.3f)
    val textColor = if (isFocused) Ink else Color.White
    Row(
        modifier
            .zIndex(if (isFocused) 1f else 0f)
            .size(width, height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick = onClick)
            .background(if (isFocused) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.10f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_input),
            contentDescription = null,
            modifier = Modifier.size(height * 0.4f),
            colorFilter = ColorFilter.tint(textColor),
        )
        BasicText(
            text = source.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

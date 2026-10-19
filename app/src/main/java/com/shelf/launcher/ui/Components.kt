package com.shelf.launcher.ui

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.data.TvApp
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date

private val CondensedBold = FontFamily(
    Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
)

/** Full-screen artwork that cross-fades whenever focus moves to another app. */
@Composable
fun Backdrop(app: TvApp?, modifier: Modifier = Modifier) {
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
            current?.backdrop?.let { art ->
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

/** The focused app's name, big and condensed, in the top corner. */
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

/** Small frosted pill with the time and a profile dot. */
@Composable
fun ClockPill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(DateFormat.getTimeFormat(context).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            now = DateFormat.getTimeFormat(context).format(Date())
            val secondsLeft = 60 - Calendar.getInstance().get(Calendar.SECOND)
            delay(secondsLeft.coerceAtLeast(1) * 1000L)
        }
    }
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.16f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .padding(start = 14.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
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

/**
 * The frosted dock. There's no live blur on Android 10, but the backdrop is
 * already blurred, so a light translucent fill with a bright top edge reads as glass.
 */
@Composable
fun GlassTray(
    modifier: Modifier = Modifier,
    padH: Dp,
    padV: Dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 22)
    Box(
        modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.13f), shape)
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

/** One app in the dock: grows, lifts and catches a glare when focused. */
@Composable
fun AppTile(
    app: TvApp,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.10f else 1f, tween(180), label = "scale")
    val glare by animateFloatAsState(if (isFocused) 0.38f else 0.10f, tween(180), label = "glare")
    val shape = RoundedCornerShape(height * 0.19f)
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier
            .zIndex(if (isFocused) 1f else 0f)
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
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
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
        // Glare + bright rim
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
                .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
        )
    }
}

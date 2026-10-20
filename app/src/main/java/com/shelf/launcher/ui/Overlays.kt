package com.shelf.launcher.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.shelf.launcher.LauncherViewModel
import com.shelf.launcher.R
import com.shelf.launcher.data.ThemeMode
import com.shelf.launcher.data.TvApp
import com.shelf.launcher.data.TvSource
import kotlin.math.roundToInt

private val Full = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)

/**
 * A full-screen dialog that places [content] just below [anchor], lined up with
 * the anchor's right edge (the top-right buttons), with a short grow-in.
 */
@Composable
private fun AnchoredPanel(anchor: Rect?, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    Dialog(onDismissRequest = onDismiss, properties = Full) {
        Layout(
            content = {
                AnimatedVisibility(
                    visibleState = appear,
                    enter = fadeIn(tween(150)) + scaleIn(
                        tween(220),
                        initialScale = 0.92f,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f),
                    ),
                ) { content() }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val panel = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val margin = 24.dp.roundToPx()
            val maxX = (constraints.maxWidth - margin - panel.width).coerceAtLeast(margin)
            val x = if (anchor != null) (anchor.right.roundToInt() - panel.width).coerceIn(margin, maxX) else maxX
            val y = if (anchor != null) anchor.bottom.roundToInt() + 14.dp.roundToPx() else margin * 3
            layout(constraints.maxWidth, constraints.maxHeight) { panel.place(x, y) }
        }
    }
}

@Composable
private fun PanelTitle(text: String, trailing: String? = null) {
    val colors = LocalShelfColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = text,
            modifier = Modifier.weight(1f),
            style = TextStyle(fontFamily = ShelfFont, color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        )
        if (trailing != null) {
            BasicText(trailing, style = TextStyle(fontFamily = ShelfFont, color = colors.subText, fontSize = 13.sp))
        }
    }
}

/** Wide tile: round icon, name and value. Like Control Center on tvOS. */
@Composable
private fun CcTile(
    @DrawableRes icon: Int,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalShelfColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(140), label = "ccTile")
    val text = if (focused) colors.focusText else colors.text
    Row(
        modifier
            .height(62.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .background(if (focused) colors.focusFill else colors.chip, CircleShape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(
                    when {
                        on && !focused -> colors.text
                        focused -> colors.focusText.copy(alpha = 0.1f)
                        else -> colors.badge
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                colorFilter = ColorFilter.tint(if (on && !focused) colors.focusText else text),
            )
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = ShelfFont, color = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            BasicText(
                value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = ShelfFont, color = text.copy(alpha = 0.7f), fontSize = 13.sp),
            )
        }
    }
}

/** Round button with a caption underneath. */
@Composable
private fun CcRound(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    val colors = LocalShelfColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(140), label = "ccRound")
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .onFocusChanged { focused = it.isFocused }
                .tvClickable(onClick = onClick)
                .background(if (focused) colors.focusFill else colors.chip, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(if (focused) colors.focusText else colors.text),
            )
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = ShelfFont, color = colors.subText, fontSize = 12.sp, textAlign = TextAlign.Center),
        )
    }
}

/** Volume bar: Left and Right change it, OK mutes. */
@Composable
private fun CcVolume(controls: LauncherViewModel.Controls, onStep: (Int) -> Unit, onMute: () -> Unit) {
    val colors = LocalShelfColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, tween(140), label = "ccVolume")
    val fraction = if (controls.muted) 0f else controls.volume.toFloat() / controls.maxVolume
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                val down = event.type == KeyEventType.KeyDown
                when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { if (down) onStep(if (rtl) 1 else -1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) onStep(if (rtl) -1 else 1); true }
                    else -> false
                }
            }
            .tvClickable(onClick = onMute)
            .clip(shape)
            .background(colors.chip)
            .then(if (focused) Modifier.border(3.dp, colors.focusFill, shape) else Modifier)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(if (colors.dark) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.9f))
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(if (controls.muted) R.drawable.ic_mute else R.drawable.ic_volume),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(colors.text),
            )
            BasicText(
                stringResource(R.string.volume),
                modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = ShelfFont, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            BasicText(
                if (controls.muted) stringResource(R.string.muted) else "${controls.volume}",
                style = TextStyle(fontFamily = ShelfFont, color = colors.text, fontSize = 15.sp),
            )
        }
    }
}

/**
 * Control Center, opened from the round button at the top right: sound, theme,
 * sleep timer, picture, Shelf and TV settings, Wi-Fi, screen off, power, volume.
 */
@Composable
fun ControlCenter(
    anchor: Rect?,
    frost: Frost?,
    controls: LauncherViewModel.Controls,
    theme: ThemeMode,
    onMute: () -> Unit,
    onVolume: (Int) -> Unit,
    onTheme: () -> Unit,
    onSleep: () -> Unit,
    onPicture: () -> Unit,
    onSettings: () -> Unit,
    onTvSettings: () -> Unit,
    onWifi: () -> Unit,
    onScreenOff: () -> Unit,
    onPower: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val network = when (controls.network) {
        LauncherViewModel.Network.WIFI -> stringResource(R.string.wifi)
        LauncherViewModel.Network.ETHERNET -> stringResource(R.string.ethernet)
        LauncherViewModel.Network.OFFLINE -> stringResource(R.string.offline)
        LauncherViewModel.Network.UNKNOWN -> null
    }
    AnchoredPanel(anchor, onDismiss) {
        GlassPanel(Modifier.width(450.dp), frost = frost, shape = RoundedCornerShape(34.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PanelTitle(stringResource(R.string.control_center), network)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CcTile(
                        icon = if (controls.muted) R.drawable.ic_mute else R.drawable.ic_volume,
                        title = stringResource(R.string.sound),
                        value = stringResource(if (controls.muted) R.string.muted else R.string.on),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(first),
                        on = controls.muted,
                        onClick = onMute,
                    )
                    CcTile(
                        icon = R.drawable.ic_theme,
                        title = stringResource(R.string.theme),
                        value = stringResource(theme.label),
                        modifier = Modifier.weight(1f),
                        onClick = onTheme,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CcTile(
                        icon = R.drawable.ic_timer,
                        title = stringResource(R.string.sleep_timer),
                        value = if (controls.sleepMinutes > 0) stringResource(R.string.minutes, controls.sleepMinutes)
                        else stringResource(R.string.off),
                        modifier = Modifier.weight(1f),
                        on = controls.sleepMinutes > 0,
                        onClick = onSleep,
                    )
                    CcTile(
                        icon = R.drawable.ic_tv,
                        title = stringResource(R.string.picture),
                        value = stringResource(R.string.settings),
                        modifier = Modifier.weight(1f),
                        onClick = onPicture,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CcRound(R.drawable.ic_settings, stringResource(R.string.settings), onSettings)
                    CcRound(R.drawable.ic_tv, stringResource(R.string.settings_tv), onTvSettings)
                    CcRound(R.drawable.ic_wifi, stringResource(R.string.wifi), onWifi)
                    CcRound(R.drawable.ic_moon, stringResource(R.string.screen_off), onScreenOff)
                    CcRound(R.drawable.ic_power, stringResource(R.string.power), onPower)
                }
                CcVolume(controls, onVolume, onMute)
            }
        }
    }
    LaunchedEffect(Unit) { first.requestWithRetry() }
}

/** The TV's HDMI inputs, opened from the top-right button. */
@Composable
fun InputsPanel(
    anchor: Rect?,
    frost: Frost?,
    sources: List<TvSource>,
    onPick: (TvSource) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    AnchoredPanel(anchor, onDismiss) {
        GlassPanel(Modifier.width(280.dp), frost = frost, shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PanelTitle(stringResource(R.string.inputs))
                sources.forEachIndexed { index, source ->
                    MenuRow(
                        text = source.label,
                        icon = R.drawable.ic_input,
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        onClick = { onPick(source) },
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { first.requestWithRetry() }
}

/** One app inside an open folder; its name shows under it when focused. */
@Composable
private fun FolderAppTile(
    app: TvApp,
    width: Dp,
    focus: FocusRequester?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = LocalShelfColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, tween(160), label = "folderTile")
    val height = width * 9f / 16f
    val shape = RoundedCornerShape(height * 0.18f)
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            (if (focus != null) Modifier.focusRequester(focus) else Modifier)
                .zIndex(if (focused) 1f else 0f)
                .size(width, height)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = (if (focused) 20.dp else 2.dp).toPx()
                    this.shape = shape
                    clip = true
                }
                .onFocusChanged { focused = it.isFocused }
                .tvClickable(onLongClick = onLongClick, onClick = onClick)
                .background(Brush.verticalGradient(listOf(app.tint, app.tintDark)))
        ) {
            when {
                app.banner != null -> Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                app.icon != null -> Image(
                    app.icon,
                    app.label,
                    Modifier
                        .fillMaxSize(0.55f)
                        .align(Alignment.Center),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        BasicText(
            if (focused) app.label else "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = ShelfFont, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** An open folder: its name and its apps in a grid, on frosted glass. */
@Composable
fun FolderPanel(
    folder: TvApp,
    frost: Frost?,
    onLaunch: (TvApp) -> Unit,
    onLongClick: (TvApp) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    Dialog(onDismissRequest = onDismiss, properties = Full) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(visibleState = appear, enter = fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.94f)) {
                GlassPanel(Modifier.width(760.dp), frost = frost, shape = RoundedCornerShape(36.dp)) {
                    Column(Modifier.padding(horizontal = 28.dp, vertical = 22.dp)) {
                        BasicText(
                            folder.label,
                            modifier = Modifier.padding(bottom = 18.dp),
                            style = TextStyle(
                                fontFamily = ShelfFont,
                                color = LocalShelfColors.current.text,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        folder.children.chunked(4).forEachIndexed { rowIndex, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                row.forEachIndexed { col, app ->
                                    FolderAppTile(
                                        app = app,
                                        width = 160.dp,
                                        focus = if (rowIndex == 0 && col == 0) first else null,
                                        onClick = { onLaunch(app) },
                                        onLongClick = { onLongClick(app) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(folder.children.size) { first.requestWithRetry() }
}

/** Rename a folder with the TV's on-screen keyboard. */
@Composable
fun RenameDialog(
    current: String,
    frost: Frost?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalShelfColors.current
    var text by remember { mutableStateOf(current) }
    val field = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = Full) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassPanel(Modifier.width(440.dp), frost = frost, shape = RoundedCornerShape(30.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PanelTitle(stringResource(R.string.folder_name))
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it.take(30) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = ShelfFont, color = colors.text, fontSize = 20.sp),
                        cursorBrush = SolidColor(colors.text),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(field)
                            .onFocusChanged { fieldFocused = it.isFocused }
                            .background(colors.chip, RoundedCornerShape(14.dp))
                            .then(
                                if (fieldFocused) Modifier.border(2.dp, colors.text, RoundedCornerShape(14.dp)) else Modifier
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                    MenuRow(text = stringResource(R.string.done), icon = R.drawable.ic_open, onClick = { onSave(text) })
                }
            }
        }
    }
    LaunchedEffect(Unit) { field.requestWithRetry() }
}

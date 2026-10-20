package com.shelf.launcher.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shelf.launcher.R
import com.shelf.launcher.data.ClockMode
import com.shelf.launcher.data.ShelfPrefs
import com.shelf.launcher.data.TvApp
import com.shelf.launcher.data.next
import com.shelf.launcher.update.UpdateState
import kotlin.math.roundToInt

private val Ink = Color(0xFF0B0E13)

private val FullScreen = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)

/** One action in an app's menu. */
data class MenuAction(@StringRes val label: Int, @DrawableRes val icon: Int, val onClick: () -> Unit)

/** Dark frosted glass with a bright top edge, like tvOS menus. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .shadow(28.dp, shape, clip = false)
            .background(Brush.verticalGradient(listOf(Color(0xF22B303A), Color(0xF21A1E26))), shape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f))
                ),
                shape = shape,
            )
    ) {
        content()
    }
}

/** A row in a menu or in settings. tvOS style: the focused row turns white and grows a little. */
@Composable
fun MenuRow(
    text: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    @DrawableRes icon: Int? = null,
    chevron: Boolean = false,
    height: Dp = 48.dp,
    fontSize: TextUnit = 17.sp,
    idleFill: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, tween(140), label = "row")
    val shape = RoundedCornerShape(height * 0.3f)
    val color = if (focused) Ink else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .background(if (focused) Color.White else idleFill, shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            text = text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = ShelfFont, color = color, fontSize = fontSize, fontWeight = FontWeight.Medium),
        )
        if (value != null) {
            BasicText(
                text = value,
                maxLines = 1,
                style = TextStyle(fontFamily = ShelfFont, color = color.copy(alpha = 0.6f), fontSize = fontSize * 0.9f),
            )
        }
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(color),
            )
        }
        if (chevron) {
            Image(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(color.copy(alpha = 0.5f)),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 6.dp),
        style = TextStyle(
            fontFamily = ShelfFont,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

@Composable
private fun HintText(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        style = TextStyle(fontFamily = ShelfFont, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, lineHeight = 21.sp),
    )
}

/**
 * Long-press menu for one app. Opens just above the app's tile (or below it, or
 * centered, when there's no room), the way tvOS menus grow out of the icon.
 */
@Composable
fun ActionMenu(
    title: String,
    anchor: Rect?,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    Dialog(onDismissRequest = onDismiss, properties = FullScreen) {
        Layout(
            content = {
                AnimatedVisibility(
                    visibleState = appear,
                    enter = fadeIn(tween(150)) +
                        scaleIn(tween(200), initialScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)),
                ) {
                    GlassPanel(Modifier.width(320.dp)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BasicText(
                                text = title,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontFamily = ShelfFont,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            actions.forEachIndexed { index, action ->
                                MenuRow(
                                    text = stringResource(action.label),
                                    icon = action.icon,
                                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                                    onClick = action.onClick,
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val menu = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val margin = 16.dp.roundToPx()
            val gap = 18.dp.roundToPx()
            val maxX = (constraints.maxWidth - margin - menu.width).coerceAtLeast(margin)
            val maxY = (constraints.maxHeight - margin - menu.height).coerceAtLeast(margin)
            val (x, y) = if (anchor == null) {
                (constraints.maxWidth - menu.width) / 2 to (constraints.maxHeight - menu.height) / 2
            } else {
                val centered = anchor.center.x.roundToInt() - menu.width / 2
                val above = anchor.top.roundToInt() - gap - menu.height
                val below = anchor.bottom.roundToInt() + gap
                val top = when {
                    above >= margin -> above
                    below <= maxY -> below
                    else -> margin
                }
                centered.coerceIn(margin, maxX) to top.coerceIn(margin, maxY)
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                menu.place(x, y) // screen position, not mirrored: the dock is always left to right
            }
        }
    }
    LaunchedEffect(Unit) { first.requestWithRetry() }
}


/** The pages of the settings sidebar. */
enum class SettingsPage(@StringRes val title: Int, @DrawableRes val icon: Int) {
    MAIN(R.string.settings, R.drawable.ic_settings),
    APPEARANCE(R.string.settings_appearance, R.drawable.ic_palette),
    APPS(R.string.settings_apps, R.drawable.ic_apps),
    HOME(R.string.settings_home_button, R.drawable.ic_home),
    UPDATES(R.string.settings_updates, R.drawable.ic_update),
    ABOUT(R.string.settings_about, R.drawable.ic_info),
}

/**
 * A row in the sidebar, like tvOS: an optional round icon, the label, an optional
 * value at the far end. The focused row turns white.
 */
@Composable
private fun SidebarRow(
    text: String,
    focus: FocusRequester? = null,
    @DrawableRes icon: Int? = null,
    value: String? = null,
    chevron: Boolean = false,
    onClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(140), label = "sidebarRow")
    val shape = RoundedCornerShape(12.dp)
    val color = if (focused) Ink else Color.White
    Row(
        (if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .fillMaxWidth()
            .height(46.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .background(if (focused) Color.White else Color.Transparent, shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(if (focused) Ink.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    colorFilter = ColorFilter.tint(color),
                )
            }
        } else {
            Spacer(Modifier.width(2.dp))
        }
        BasicText(
            text = text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = ShelfFont, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        )
        if (value != null) {
            BasicText(
                text = value,
                maxLines = 1,
                style = TextStyle(fontFamily = ShelfFont, color = color.copy(alpha = 0.6f), fontSize = 14.sp),
            )
        }
        if (chevron) {
            Image(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(color.copy(alpha = 0.45f)),
            )
        }
    }
}

/** Small gray section title inside the sidebar, like "Channels & Apps" in tvOS. */
@Composable
private fun SidebarSection(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
        style = TextStyle(
            fontFamily = ShelfFont,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

/** Top of the sidebar: profile, name and time on the main page; back arrow and title on the others. */
@Composable
private fun SidebarHeader(page: SettingsPage, clock: ClockMode) {
    val context = LocalContext.current
    val time = remember(clock) { formatTime(context, clock) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (page == SettingsPage.MAIN) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFF29E6B), Color(0xFFC2557A))))
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            )
            BasicText(
                text = stringResource(R.string.app_name),
                modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = ShelfFont, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
            BasicText(
                text = time,
                style = TextStyle(fontFamily = ShelfFont, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = 180f }, // points back
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.7f)),
            )
            BasicText(
                text = stringResource(page.title),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = ShelfFont, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun updateAction(state: UpdateState): String = when (state) {
    UpdateState.Idle -> stringResource(R.string.update_check)
    UpdateState.Checking -> stringResource(R.string.update_checking)
    UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
    is UpdateState.Available -> stringResource(R.string.update_install, state.build)
    is UpdateState.Downloading -> stringResource(R.string.update_downloading, (state.progress * 100).toInt())
    UpdateState.Installing -> stringResource(R.string.update_installing)
    is UpdateState.Failed -> state.reason
}

/**
 * Launcher settings as a tvOS-style sidebar: a floating glass panel on the side,
 * tinted with the colors of the screen behind it. Opening a category slides the
 * panel to that page; Back goes up a page, then closes.
 */
@Composable
fun SettingsPanel(
    prefs: ShelfPrefs,
    hiddenApps: List<TvApp>,
    hasSources: Boolean,
    update: UpdateState,
    versionName: String,
    homeServiceOn: Boolean,
    tint: Color,
    onHomeSetup: () -> Unit,
    onChange: ((ShelfPrefs) -> ShelfPrefs) -> Unit,
    onUnhide: (String) -> Unit,
    onResetOrder: () -> Unit,
    onUpdateClick: () -> Unit,
    onSystemSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.MAIN) }
    var returnTo by remember { mutableStateOf(SettingsPage.APPEARANCE) }
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    fun open(target: SettingsPage) {
        returnTo = target
        page = target
    }

    Dialog(
        onDismissRequest = { if (page != SettingsPage.MAIN) page = SettingsPage.MAIN else onDismiss() },
        properties = FullScreen,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Darken the side the sidebar sits on, so it stands out from the screen.
            AnimatedVisibility(visibleState = appear, enter = fadeIn(tween(260))) {
                val shade = listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.15f), Color.Transparent)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(if (rtl) shade.reversed() else shade))
                )
            }

            AnimatedVisibility(
                visibleState = appear,
                modifier = Modifier.align(Alignment.CenterStart),
                enter = fadeIn(tween(200)) + slideInHorizontally(tween(300)) { w -> if (rtl) w / 3 else -w / 3 },
            ) {
                val shape = RoundedCornerShape(30.dp)
                val top = lerp(tint, Color.Black, 0.35f).copy(alpha = 0.9f)
                val bottom = lerp(tint, Color.Black, 0.7f).copy(alpha = 0.93f)
                Box(
                    Modifier
                        .padding(start = 28.dp, top = 28.dp, bottom = 28.dp)
                        .width(360.dp)
                        .fillMaxHeight()
                        .shadow(30.dp, shape, clip = false)
                        .background(Brush.verticalGradient(listOf(top, bottom)), shape)
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.06f))
                            ),
                            shape = shape,
                        )
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            val forward = targetState != SettingsPage.MAIN
                            val sign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
                            (fadeIn(tween(200)) + slideInHorizontally(tween(240)) { it / 6 * sign }) togetherWith
                                fadeOut(tween(100))
                        },
                        label = "sidebarPage",
                    ) { p ->
                        Column(Modifier.fillMaxSize()) {
                            SidebarHeader(p, prefs.clock)
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.weight(1f)) {
                                when (p) {
                                    SettingsPage.MAIN -> MainPage(
                                        returnTo = returnTo,
                                        homeServiceOn = homeServiceOn,
                                        update = update,
                                        versionName = versionName,
                                        onOpen = ::open,
                                        onSystemSettings = onSystemSettings,
                                    )
                                    SettingsPage.APPEARANCE -> AppearancePage(prefs, hasSources, onChange)
                                    SettingsPage.APPS -> AppsPage(hiddenApps, onUnhide, onResetOrder)
                                    SettingsPage.HOME -> HomePage(homeServiceOn, onHomeSetup)
                                    SettingsPage.UPDATES -> UpdatesPage(update, versionName, onUpdateClick)
                                    SettingsPage.ABOUT -> AboutPage(versionName)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun MainPage(
    returnTo: SettingsPage,
    homeServiceOn: Boolean,
    update: UpdateState,
    versionName: String,
    onOpen: (SettingsPage) -> Unit,
    onSystemSettings: () -> Unit,
) {
    val focus = remember { SettingsPage.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { focus.getValue(returnTo).requestWithRetry() }
    val updateValue = if (update is UpdateState.Available) stringResource(R.string.update_available) else versionName
    SidebarList {
        item { SidebarSection(stringResource(R.string.settings)) }
        for (target in listOf(SettingsPage.APPEARANCE, SettingsPage.APPS, SettingsPage.HOME, SettingsPage.UPDATES, SettingsPage.ABOUT)) {
            item(key = target.name) {
                SidebarRow(
                    text = stringResource(target.title),
                    focus = focus[target],
                    icon = target.icon,
                    value = when (target) {
                        SettingsPage.HOME -> stringResource(if (homeServiceOn) R.string.on else R.string.off)
                        SettingsPage.UPDATES -> updateValue
                        else -> null
                    },
                ) { onOpen(target) }
            }
        }
        item { SidebarSection(stringResource(R.string.settings_system)) }
        item {
            SidebarRow(stringResource(R.string.settings_tv), icon = R.drawable.ic_tv, onClick = onSystemSettings)
        }
    }
}

@Composable
private fun AppearancePage(
    prefs: ShelfPrefs,
    hasSources: Boolean,
    onChange: ((ShelfPrefs) -> ShelfPrefs) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWithRetry() }
    SidebarList {
        item {
            SidebarRow(stringResource(R.string.apps_per_row), first, value = "${prefs.columns}") {
                onChange { it.copy(columns = if (it.columns >= 7) 5 else it.columns + 1) }
            }
        }
        item {
            SidebarRow(stringResource(R.string.clock), value = stringResource(prefs.clock.label)) {
                onChange { it.copy(clock = it.clock.next()) }
            }
        }
        item {
            SidebarRow(stringResource(R.string.glass), value = stringResource(prefs.glass.label)) {
                onChange { it.copy(glass = it.glass.next()) }
            }
        }
        item {
            SidebarRow(stringResource(R.string.backdrop), value = stringResource(prefs.backdrop.label)) {
                onChange { it.copy(backdrop = it.backdrop.next()) }
            }
        }
        if (hasSources) {
            item {
                SidebarRow(
                    text = stringResource(R.string.hdmi_row),
                    value = stringResource(if (prefs.showInputs) R.string.shown else R.string.hidden),
                ) { onChange { it.copy(showInputs = !it.showInputs) } }
            }
        }
    }
}

@Composable
private fun AppsPage(
    hiddenApps: List<TvApp>,
    onUnhide: (String) -> Unit,
    onResetOrder: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWithRetry() }
    SidebarList {
        item {
            SidebarRow(
                text = stringResource(R.string.reset_order),
                focus = first,
                value = stringResource(R.string.reset_order_value),
                onClick = onResetOrder,
            )
        }
        item { SidebarSection(stringResource(R.string.hidden_apps)) }
        if (hiddenApps.isEmpty()) {
            item { HintText(stringResource(R.string.no_hidden_apps)) }
        } else {
            items(hiddenApps, key = { it.key }) { app ->
                SidebarRow(app.label, value = stringResource(R.string.show)) { onUnhide(app.key) }
            }
        }
    }
}

@Composable
private fun HomePage(homeServiceOn: Boolean, onHomeSetup: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWithRetry() }
    SidebarList {
        item {
            SidebarRow(
                text = stringResource(R.string.home_status),
                focus = first,
                value = stringResource(if (homeServiceOn) R.string.on else R.string.off),
                onClick = onHomeSetup,
            )
        }
        item { SidebarRow(stringResource(R.string.home_open_settings), chevron = true, onClick = onHomeSetup) }
        item { HintText(stringResource(R.string.home_explain)) }
    }
}

@Composable
private fun UpdatesPage(update: UpdateState, versionName: String, onUpdateClick: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWithRetry() }
    SidebarList {
        item { SidebarRow(updateAction(update), first, onClick = onUpdateClick) }
        item { SidebarRow(stringResource(R.string.version), value = versionName) }
        item { HintText(stringResource(R.string.update_explain)) }
    }
}

@Composable
private fun AboutPage(versionName: String) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWithRetry() }
    SidebarList {
        item { HintText(stringResource(R.string.about_text)) }
        item { SidebarRow(stringResource(R.string.version), first, value = versionName) }
        item { SidebarRow(stringResource(R.string.source_code), value = "GitHub") }
        item { SidebarRow(stringResource(R.string.font), value = "Assistant") }
    }
}

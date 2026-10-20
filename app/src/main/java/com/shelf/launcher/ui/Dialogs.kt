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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
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

/** The pages of the settings screen. */
enum class SettingsPage(@StringRes val title: Int, @DrawableRes val icon: Int) {
    MAIN(R.string.settings, R.drawable.ic_settings),
    APPEARANCE(R.string.settings_appearance, R.drawable.ic_palette),
    APPS(R.string.settings_apps, R.drawable.ic_apps),
    HOME(R.string.settings_home_button, R.drawable.ic_home),
    UPDATES(R.string.settings_updates, R.drawable.ic_update),
    ABOUT(R.string.settings_about, R.drawable.ic_info),
}

@Composable
private fun SettingRow(
    text: String,
    focus: FocusRequester? = null,
    value: String? = null,
    chevron: Boolean = false,
    onClick: () -> Unit = {},
) {
    MenuRow(
        text = text,
        value = value,
        chevron = chevron,
        height = 56.dp,
        fontSize = 18.sp,
        idleFill = Color.White.copy(alpha = 0.07f),
        modifier = if (focus != null) Modifier.focusRequester(focus) else Modifier,
        onClick = onClick,
    )
}

@Composable
private fun SettingsList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp),
        content = content,
    )
}

/** Big icon and the page name, on the side of the settings screen. */
@Composable
private fun SettingsHeader(page: SettingsPage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val shape = RoundedCornerShape(38.dp)
        Box(
            Modifier
                .size(160.dp)
                .shadow(24.dp, shape, clip = false)
                .background(Brush.linearGradient(listOf(Color(0xFF424A5A), Color(0xFF1C212A))), shape)
                .border(1.dp, Color.White.copy(alpha = 0.2f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(page.icon),
                contentDescription = null,
                modifier = Modifier.size(76.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
        Spacer(Modifier.height(26.dp))
        BasicText(
            text = stringResource(page.title),
            style = TextStyle(fontFamily = ShelfFont, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold),
        )
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
 * Launcher settings, full screen like tvOS: a big icon for the current page on one
 * side, its options on the other. Back goes up a page, then closes.
 */
@Composable
fun SettingsPanel(
    prefs: ShelfPrefs,
    hiddenApps: List<TvApp>,
    hasSources: Boolean,
    update: UpdateState,
    versionName: String,
    homeServiceOn: Boolean,
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
        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn(tween(200)) + scaleIn(tween(260), initialScale = 1.04f),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF1A1F29), Color(0xFF0B0E13), Color(0xFF07090D))))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 64.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = page,
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight(),
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                        label = "settingsHeader",
                    ) { p -> SettingsHeader(p) }

                    AnimatedContent(
                        targetState = page,
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight(),
                        transitionSpec = {
                            val forward = targetState != SettingsPage.MAIN
                            val sign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
                            (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 * sign }) togetherWith
                                fadeOut(tween(120))
                        },
                        label = "settingsList",
                    ) { p ->
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
    SettingsList {
        item {
            SettingRow(stringResource(R.string.settings_appearance), focus[SettingsPage.APPEARANCE], chevron = true) {
                onOpen(SettingsPage.APPEARANCE)
            }
        }
        item {
            SettingRow(stringResource(R.string.settings_apps), focus[SettingsPage.APPS], chevron = true) {
                onOpen(SettingsPage.APPS)
            }
        }
        item {
            SettingRow(
                text = stringResource(R.string.settings_home_button),
                focus = focus[SettingsPage.HOME],
                value = stringResource(if (homeServiceOn) R.string.on else R.string.off),
                chevron = true,
            ) { onOpen(SettingsPage.HOME) }
        }
        item {
            SettingRow(stringResource(R.string.settings_updates), focus[SettingsPage.UPDATES], updateValue, chevron = true) {
                onOpen(SettingsPage.UPDATES)
            }
        }
        item {
            SettingRow(stringResource(R.string.settings_about), focus[SettingsPage.ABOUT], chevron = true) {
                onOpen(SettingsPage.ABOUT)
            }
        }
        item { SettingRow(stringResource(R.string.settings_tv), onClick = onSystemSettings) }
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
    SettingsList {
        item {
            SettingRow(stringResource(R.string.apps_per_row), first, "${prefs.columns}") {
                onChange { it.copy(columns = if (it.columns >= 7) 5 else it.columns + 1) }
            }
        }
        item {
            SettingRow(stringResource(R.string.clock), value = stringResource(prefs.clock.label)) {
                onChange { it.copy(clock = it.clock.next()) }
            }
        }
        item {
            SettingRow(stringResource(R.string.glass), value = stringResource(prefs.glass.label)) {
                onChange { it.copy(glass = it.glass.next()) }
            }
        }
        item {
            SettingRow(stringResource(R.string.backdrop), value = stringResource(prefs.backdrop.label)) {
                onChange { it.copy(backdrop = it.backdrop.next()) }
            }
        }
        if (hasSources) {
            item {
                SettingRow(
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
    SettingsList {
        item {
            SettingRow(stringResource(R.string.reset_order), first, stringResource(R.string.reset_order_value), onClick = onResetOrder)
        }
        item { SectionLabel(stringResource(R.string.hidden_apps)) }
        if (hiddenApps.isEmpty()) {
            item { HintText(stringResource(R.string.no_hidden_apps)) }
        } else {
            items(hiddenApps, key = { it.key }) { app ->
                SettingRow(app.label, value = stringResource(R.string.show)) { onUnhide(app.key) }
            }
        }
    }
}

@Composable
private fun HomePage(homeServiceOn: Boolean, onHomeSetup: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWithRetry() }
    SettingsList {
        item {
            SettingRow(
                text = stringResource(R.string.home_status),
                focus = first,
                value = stringResource(if (homeServiceOn) R.string.on else R.string.off),
                onClick = onHomeSetup,
            )
        }
        item { SettingRow(stringResource(R.string.home_open_settings), chevron = true, onClick = onHomeSetup) }
        item { HintText(stringResource(R.string.home_explain)) }
    }
}

@Composable
private fun UpdatesPage(update: UpdateState, versionName: String, onUpdateClick: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWithRetry() }
    SettingsList {
        item { SettingRow(updateAction(update), first, onClick = onUpdateClick) }
        item { SettingRow(stringResource(R.string.version), value = versionName) }
        item { HintText(stringResource(R.string.update_explain)) }
    }
}

@Composable
private fun AboutPage(versionName: String) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWithRetry() }
    SettingsList {
        item { HintText(stringResource(R.string.about_text)) }
        item { SettingRow(stringResource(R.string.version), first, versionName) }
        item { SettingRow(stringResource(R.string.source_code), value = "github.com/dvir9032/tvOS-Launcher") }
        item { SettingRow(stringResource(R.string.font), value = "Assistant · SIL OFL") }
    }
}

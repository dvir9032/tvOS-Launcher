package com.shelf.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shelf.launcher.LauncherViewModel
import com.shelf.launcher.data.SOURCE_PREFIX
import com.shelf.launcher.data.TvApp
import com.shelf.launcher.data.arrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
private val MinimalBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailing = offset + size
        return when {
            offset >= 0f && trailing <= containerSize -> 0f // already fully visible
            offset < 0f && trailing > containerSize -> 0f   // bigger than the screen
            offset < 0f -> offset                            // cut off at the top
            else -> trailing - containerSize                 // cut off at the bottom
        }
    }
}

/**
 * The home screen: full-screen artwork of the focused app, its name in the top
 * corner, a top bar (update, clock, settings), a glass dock with the first row
 * of apps, the HDMI inputs, and the remaining rows peeking out underneath.
 *
 * All sizes are fractions of the screen so it looks the same on any TV density.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfHome(vm: LauncherViewModel) {
    val allApps by vm.apps.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val sources by vm.sources.collectAsState()
    val update by vm.update.collectAsState()
    val homePresses by vm.homePresses.collectAsState()
    val homeSetupNeeded by vm.homeSetupNeeded.collectAsState()
    val homeServiceOn by vm.homeServiceOn.collectAsState()

    val columns = prefs.columns
    val arranged = remember(allApps, prefs.order) { arrange(allApps, prefs.order) }
    val visible = remember(arranged, prefs.hidden) { arranged.filter { it.key !in prefs.hidden } }
    val hiddenApps = remember(arranged, prefs.hidden) { arranged.filter { it.key in prefs.hidden } }
    val shownSources = if (prefs.showInputs) sources else emptyList()
    val rows = remember(visible, columns) { visible.chunked(columns) }

    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val focusedApp = visible.firstOrNull { it.key == focusedKey }
    val focusedSource = shownSources.firstOrNull { SOURCE_PREFIX + it.id == focusedKey }
    val title = focusedApp?.label ?: focusedSource?.label ?: visible.firstOrNull()?.label.orEmpty()
    val backdropApp = if (focusedSource != null) null else (focusedApp ?: visible.firstOrNull())

    var menuFor by remember { mutableStateOf<TvApp?>(null) }
    var movingKey by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // One FocusRequester per item, so focus can follow a tile when it moves.
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requesterFor(key: String) = requesters.getOrPut(key) { FocusRequester() }
    var focusTarget by remember { mutableStateOf<String?>(null) }
    var focusNonce by remember { mutableIntStateOf(0) }
    fun focusOn(key: String?) {
        if (key == null) return
        focusTarget = key
        focusNonce++
    }
    LaunchedEffect(focusNonce) {
        val key = focusTarget ?: return@LaunchedEffect
        requesterFor(key).requestWithRetry()
    }

    fun lazyIndexOfRow(row: Int) = if (row == 0) 0 else row + (if (shownSources.isNotEmpty()) 1 else 0)

    fun backToTop() {
        movingKey = null
        scope.launch { listState.scrollToItem(0) }
        focusOn(visible.firstOrNull()?.key)
    }

    fun move(dir: MoveDir) {
        val key = movingKey ?: return
        val keys = visible.map { it.key }.toMutableList()
        val from = keys.indexOf(key)
        if (from < 0) return
        val delta = when (dir) {
            MoveDir.LEFT -> -1
            MoveDir.RIGHT -> 1
            MoveDir.UP -> -columns
            MoveDir.DOWN -> columns
        }
        val to = (from + delta).coerceIn(0, keys.lastIndex)
        if (to == from) return
        keys.removeAt(from)
        keys.add(to, key)
        vm.saveOrder(keys + hiddenApps.map { it.key })
        val item = lazyIndexOfRow(to / columns)
        scope.launch {
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == item }) listState.scrollToItem(item)
        }
        focusOn(key)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val w = maxWidth
        val h = maxHeight

        val sidePad = w * 0.023f
        val trayPadH = w * 0.016f
        val trayPadV = h * 0.024f
        val gap = w * 0.0177f
        val tileW: Dp = (w - sidePad * 2 - trayPadH * 2 - gap * (columns - 1)) / columns
        val tileH: Dp = tileW * 9f / 16f
        val trayTop = h * 0.70f

        Backdrop(app = backdropApp, style = prefs.backdrop, modifier = Modifier.fillMaxSize())

        // Darken the top (for the title) and the bottom (for the dock).
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.22f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.6f),
                    )
                )
        )

        ShelfTitle(
            text = title,
            fontSize = w * 0.068f,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = w * 0.046f, top = h * 0.06f)
                .fillMaxWidth(0.6f),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = h * 0.048f, end = w * 0.033f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (homeSetupNeeded) {
                ActionPill(text = "הגדר כפתור בית", onClick = vm::openHomeSetup, color = Color(0xFFE8862E))
            }
            UpdatePill(state = update, onClick = vm::installUpdate)
            ClockPill(mode = prefs.clock)
            GlassIconButton(onClick = { settingsOpen = true }) { focused -> SettingsGlyph(focused) }
        }

        val appRow: @Composable (List<TvApp>) -> Unit = { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { app ->
                    key(app.key) {
                        AppTile(
                            app = app,
                            width = tileW,
                            height = tileH,
                            moving = movingKey == app.key,
                            modifier = Modifier.focusRequester(requesterFor(app.key)),
                            onFocused = { focusedKey = app.key },
                            onClick = { if (movingKey == null) vm.launch(app) },
                            onLongClick = { if (movingKey == null) menuFor = app },
                            onMove = ::move,
                            onMoveDone = {
                                movingKey = null
                                focusOn(app.key)
                            },
                        )
                    }
                }
            }
        }

        // On TVs Compose scrolls the focused row to ~30% of the screen by default,
        // which pulls the dock up. Only scroll when a row is actually cut off.
        CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalBringIntoView) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = trayTop, bottom = h * 0.3f),
                verticalArrangement = Arrangement.spacedBy(h * 0.035f),
            ) {
                rows.firstOrNull()?.let { first ->
                    item(key = "dock") {
                        GlassTray(
                            modifier = Modifier.padding(horizontal = sidePad),
                            padH = trayPadH,
                            padV = trayPadV,
                            glassAlpha = prefs.glass.alpha,
                        ) { appRow(first) }
                    }
                }
                if (shownSources.isNotEmpty()) {
                    item(key = "sources") {
                        Row(
                            modifier = Modifier.padding(horizontal = sidePad + trayPadH),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            shownSources.forEach { source ->
                                key(source.id) {
                                    val sourceKey = SOURCE_PREFIX + source.id
                                    SourceTile(
                                        source = source,
                                        width = tileW,
                                        height = tileH * 0.42f,
                                        modifier = Modifier.focusRequester(requesterFor(sourceKey)),
                                        onFocused = { focusedKey = sourceKey },
                                        onClick = { vm.launchSource(source) },
                                    )
                                }
                            }
                        }
                    }
                }
                itemsIndexed(rows.drop(1), key = { index, _ -> "row_$index" }) { _, row ->
                    Box(Modifier.padding(horizontal = sidePad + trayPadH)) { appRow(row) }
                }
            }
        }
    }

    menuFor?.let { app ->
        ActionMenu(
            app = app,
            onDismiss = {
                menuFor = null
                focusOn(app.key)
            },
            onOpen = {
                menuFor = null
                vm.launch(app)
            },
            onMove = {
                menuFor = null
                movingKey = app.key
                focusOn(app.key)
            },
            onHide = {
                menuFor = null
                vm.hide(app.key)
                focusOn(visible.firstOrNull { it.key != app.key }?.key)
            },
        )
    }

    if (settingsOpen) {
        SettingsPanel(
            prefs = prefs,
            hiddenApps = hiddenApps,
            hasSources = sources.isNotEmpty(),
            update = update,
            versionName = vm.versionName,
            homeServiceOn = homeServiceOn,
            onHomeSetup = vm::openHomeSetup,
            onChange = vm::updatePrefs,
            onUnhide = vm::unhide,
            onResetOrder = vm::resetOrder,
            onUpdateClick = vm::onUpdateClicked,
            onSystemSettings = vm::openSystemSettings,
            onDismiss = {
                settingsOpen = false
                focusOn(focusedKey ?: visible.firstOrNull()?.key)
            },
        )
    }

    // Focus the first app when the list first arrives and on every Home press.
    LaunchedEffect(visible.isNotEmpty(), homePresses) {
        if (visible.isNotEmpty()) {
            menuFor = null
            settingsOpen = false
            backToTop()
        }
    }

    // A launcher has nowhere to go "back" to: jump to the top instead.
    BackHandler(enabled = movingKey == null && menuFor == null && !settingsOpen) {
        backToTop()
    }
}

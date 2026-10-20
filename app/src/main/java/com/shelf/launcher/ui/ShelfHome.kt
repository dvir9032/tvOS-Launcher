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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import com.shelf.launcher.R
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.height
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.FOLDER_PREFIX
import com.shelf.launcher.data.FolderPreset
import com.shelf.launcher.data.ThemeMode
import com.shelf.launcher.data.buildDock
import com.shelf.launcher.data.folderName
import com.shelf.launcher.data.next
import com.shelf.launcher.home.TvPower
import java.util.Calendar
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.shelf.launcher.LauncherViewModel
import com.shelf.launcher.data.SOURCE_PREFIX
import com.shelf.launcher.data.TvApp
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
@Composable
fun ShelfHome(vm: LauncherViewModel, onAllowContent: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    // Auto theme: dark from 7 pm to 6 am, checked every minute.
    val hour by produceState(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        while (true) {
            delay(60_000)
            value = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }
    }
    val dark = when (prefs.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> hour >= 19 || hour < 6
    }
    CompositionLocalProvider(LocalShelfColors provides if (dark) DarkColors else LightColors) {
        ShelfHomeContent(vm, onAllowContent)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfHomeContent(vm: LauncherViewModel, onAllowContent: () -> Unit) {
    val context = LocalContext.current
    val allApps by vm.apps.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val sources by vm.sources.collectAsState()
    val update by vm.update.collectAsState()
    val homePresses by vm.homePresses.collectAsState()
    val homeSetupNeeded by vm.homeSetupNeeded.collectAsState()
    val homeServiceOn by vm.homeServiceOn.collectAsState()
    val content by vm.content.collectAsState()
    val contentAllowed by vm.contentAllowed.collectAsState()
    val controls by vm.controls.collectAsState()

    val columns = prefs.columns
    val arranged = remember(allApps, prefs) { buildDock(context, allApps, prefs) }
    val visible = remember(arranged, prefs.hidden) { arranged.filter { it.key !in prefs.hidden } }
    val hiddenApps = remember(arranged, prefs.hidden) { arranged.filter { it.key in prefs.hidden } }
    val shownSources = if (prefs.showInputs) sources else emptyList()
    val rows = remember(visible, columns) { visible.chunked(columns) }
    val dockKeys = visible.map { it.key }
    val recentApps = remember(allApps, prefs.recent) { prefs.recent.mapNotNull { k -> allApps.firstOrNull { it.key == k } } }

    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val focusedApp = visible.firstOrNull { it.key == focusedKey } ?: allApps.firstOrNull { it.key == focusedKey }
    val focusedSource = shownSources.firstOrNull { SOURCE_PREFIX + it.id == focusedKey }
    val title = focusedApp?.label ?: focusedSource?.label ?: visible.firstOrNull()?.label.orEmpty()
    val backdropApp = if (focusedSource != null) null else (focusedApp ?: visible.firstOrNull())

    // Top shelf: content from the last focused app (shows, episodes, videos it put on
    // the TV home screen). The big picture rotates until a content card is focused.
    var ownerKey by rememberSaveable { mutableStateOf<String?>(null) }
    var shelfFocused by remember { mutableStateOf(false) }
    // 0 = the dock or above it; 1 = the rows below the dock (they fade in only then)
    var focusZone by remember { mutableIntStateOf(0) }
    val owner = visible.firstOrNull { it.key == ownerKey } ?: allApps.firstOrNull { it.key == ownerKey } ?: visible.firstOrNull()
    val shelfItems = if (prefs.showContent) owner?.packageName?.let { content[it] }.orEmpty() else emptyList()
    var heroIndex by remember(owner?.key) { mutableIntStateOf(0) }
    LaunchedEffect(owner?.key, shelfItems.size, shelfFocused) {
        if (shelfItems.size < 2 || shelfFocused) return@LaunchedEffect
        while (true) {
            delay(8_000)
            heroIndex = (heroIndex + 1) % shelfItems.size
        }
    }
    val heroItem = if (focusedSource != null) null else shelfItems.getOrNull(heroIndex % shelfItems.size.coerceAtLeast(1))

    var menuFor by remember { mutableStateOf<TvApp?>(null) }
    var menuFromRecent by remember { mutableStateOf(false) }
    var movingKey by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var controlCenterOpen by remember { mutableStateOf(false) }
    var inputsOpen by remember { mutableStateOf(false) }
    var openFolderKey by remember { mutableStateOf<String?>(null) }
    var folderPickFor by remember { mutableStateOf<TvApp?>(null) }
    var renameFolderKey by remember { mutableStateOf<String?>(null) }
    var folderAppMenu by remember { mutableStateOf<TvApp?>(null) }
    var ccBounds by remember { mutableStateOf<Rect?>(null) }
    var inputsBounds by remember { mutableStateOf<Rect?>(null) }
    val openFolder = visible.firstOrNull { it.key == openFolderKey && it.isFolder }

    fun toast(@StringRes text: Int) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

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

    // Item 0 is the content row (empty when there's none), the dock is item 1.
    fun lazyIndexOfRow(row: Int) = if (row == 0) 1 else row + 1 + (if (shownSources.isNotEmpty()) 1 else 0)

    fun backToTop() {
        movingKey = null
        scope.launch { listState.scrollToItem(0) }
        focusOn(visible.firstOrNull()?.key)
    }

    /** Puts [key] at position [to] among the visible apps, saves, and keeps focus on it. */
    fun reorder(key: String, to: Int) {
        val keys = visible.map { it.key }.toMutableList()
        val from = keys.indexOf(key)
        val target = to.coerceIn(0, keys.lastIndex)
        if (from < 0 || target == from) return
        keys.removeAt(from)
        keys.add(target, key)
        vm.saveOrder(keys + hiddenApps.map { it.key })
        val item = lazyIndexOfRow(target / columns)
        scope.launch {
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == item }) listState.scrollToItem(item)
        }
        focusOn(key)
    }

    fun move(dir: MoveDir) {
        val key = movingKey ?: return
        val from = visible.indexOfFirst { it.key == key }
        if (from < 0) return
        val delta = when (dir) {
            MoveDir.LEFT -> -1
            MoveDir.RIGHT -> 1
            MoveDir.UP -> -columns
            MoveDir.DOWN -> columns
        }
        reorder(key, from + delta)
    }

    // The home screen is drawn into a layer, so menus can show a blurred copy of it.
    val homeLayer = rememberGraphicsLayer()
    var frost by remember { mutableStateOf<Frost?>(null) }
    val menuOpen = settingsOpen || menuFor != null || controlCenterOpen || inputsOpen ||
        openFolderKey != null || folderPickFor != null || renameFolderKey != null || folderAppMenu != null
    LaunchedEffect(menuOpen, prefs, visible, shownSources) {
        if (!menuOpen) {
            frost = null
            return@LaunchedEffect
        }
        // After a setting changes the screen behind, let it settle before retaking it.
        if (frost != null) delay(400)
        captureFrost(homeLayer)?.let { frost = it }
    }

    // Where each tile sits on screen, so its menu can open right above it.
    val tileBounds = remember { mutableMapOf<String, Rect>() }

    // The home screen keeps the tvOS layout (title top-left, apps left to right) even
    // when the TV is set to a right-to-left language. Menus and settings follow the TV.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                homeLayer.record { this@drawWithContent.drawContent() }
                drawLayer(homeLayer)
            }
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
        if (heroItem != null) {
            HeroArt(item = heroItem, modifier = Modifier.fillMaxSize())
        }

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

        if (heroItem != null && owner != null) {
            HeroTitle(
                appName = owner.label,
                item = heroItem,
                fontSize = w * 0.046f,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = w * 0.046f, top = h * 0.06f)
                    .fillMaxWidth(0.52f),
            )
        } else {
            ShelfTitle(
                text = title,
                fontSize = w * 0.068f,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = w * 0.046f, top = h * 0.06f)
                    .fillMaxWidth(0.6f),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = h * 0.048f, end = w * 0.033f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (homeSetupNeeded) {
                ActionPill(
                    text = stringResource(R.string.setup_home_button),
                    onClick = vm::openHomeSetup,
                    color = Color(0xFFE8862E),
                )
            }
            UpdatePill(state = update, onClick = vm::installUpdate)
            ClockPill(mode = prefs.clock)
            if (sources.isNotEmpty()) {
                GlassIconButton(
                    icon = R.drawable.ic_input,
                    description = stringResource(R.string.inputs),
                    onClick = { inputsOpen = true },
                    modifier = Modifier.onGloballyPositioned { inputsBounds = it.boundsInRoot() },
                )
            }
            GlassIconButton(
                icon = R.drawable.ic_sliders,
                description = stringResource(R.string.control_center),
                onClick = {
                    vm.refreshControls()
                    controlCenterOpen = true
                },
                modifier = Modifier.onGloballyPositioned { ccBounds = it.boundsInRoot() },
            )
        }

        val appRow: @Composable (List<TvApp>, Boolean) -> Unit = { row, below ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { app ->
                    key(app.key) {
                        AppTile(
                            app = app,
                            width = tileW,
                            height = tileH,
                            moving = movingKey == app.key,
                            modifier = Modifier
                                .focusRequester(requesterFor(app.key))
                                .onGloballyPositioned { tileBounds[app.key] = it.boundsInRoot() },
                            onFocused = {
                                focusedKey = app.key
                                ownerKey = app.key
                                focusZone = if (below) 1 else 0
                            },
                            onClick = {
                                if (movingKey == null) {
                                    if (app.isFolder) openFolderKey = app.key else vm.launch(app)
                                }
                            },
                            onLongClick = {
                                if (movingKey == null) {
                                    menuFromRecent = false
                                    menuFor = app
                                }
                            },
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

        val spacing = h * 0.035f
        val shelfRowHeight = if (shelfItems.isNotEmpty()) SHELF_DOTS_HEIGHT else 0.dp
        val belowAlpha by animateFloatAsState(if (focusZone == 1) 1f else 0f, tween(300), label = "belowDock")

        // On TVs Compose scrolls the focused row to ~30% of the screen by default,
        // which pulls the dock up. Only scroll when a row is actually cut off.
        CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalBringIntoView) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // The dock always starts at trayTop; the content row (or an empty item) sits above it.
                contentPadding = PaddingValues(top = trayTop - spacing - shelfRowHeight, bottom = h * 0.3f),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                item(key = "shelf") {
                    if (shelfItems.isNotEmpty()) {
                        ShelfDots(
                            count = shelfItems.size,
                            index = heroIndex % shelfItems.size,
                            appName = owner?.label.orEmpty(),
                            onFocusChange = { focused ->
                                shelfFocused = focused
                                if (focused) {
                                    focusedKey = "shelf"
                                    focusZone = 0
                                }
                            },
                            onStep = { step -> heroIndex = (heroIndex + step).mod(shelfItems.size) },
                            onOpen = { heroItem?.let(vm::launchItem) },
                        )
                    } else {
                        Box(Modifier.height(0.dp))
                    }
                }
                rows.firstOrNull()?.let { first ->
                    item(key = "dock") {
                        GlassTray(
                            modifier = Modifier.padding(horizontal = sidePad),
                            padH = trayPadH,
                            padV = trayPadV,
                            glassAlpha = prefs.glass.alpha,
                        ) { appRow(first, false) }
                    }
                }
                if (shownSources.isNotEmpty()) {
                    item(key = "sources") {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = sidePad + trayPadH)
                                .graphicsLayer { alpha = belowAlpha },
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
                                        onFocused = {
                                            focusedKey = sourceKey
                                            focusZone = 1
                                        },
                                        onClick = { vm.launchSource(source) },
                                    )
                                }
                            }
                        }
                    }
                }
                itemsIndexed(rows.drop(1), key = { index, _ -> "row_$index" }) { _, row ->
                    Box(
                        Modifier
                            .padding(horizontal = sidePad + trayPadH)
                            .graphicsLayer { alpha = belowAlpha }
                    ) { appRow(row, true) }
                }
                if (recentApps.isNotEmpty()) {
                    item(key = "recent") {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = sidePad + trayPadH)
                                .graphicsLayer { alpha = belowAlpha },
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            BasicText(
                                text = stringResource(R.string.recent),
                                style = TextStyle(
                                    fontFamily = ShelfFont,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                recentApps.take(columns).forEach { app ->
                                    key("recent_" + app.key) {
                                        val recentKey = "recent:" + app.key
                                        AppTile(
                                            app = app,
                                            width = tileW,
                                            height = tileH,
                                            moving = false,
                                            modifier = Modifier
                                                .focusRequester(requesterFor(recentKey))
                                                .onGloballyPositioned { tileBounds[recentKey] = it.boundsInRoot() },
                                            onFocused = {
                                                focusedKey = app.key
                                                ownerKey = app.key
                                                focusZone = 1
                                            },
                                            onClick = { vm.launch(app) },
                                            onLongClick = {
                                                menuFromRecent = true
                                                menuFor = app
                                            },
                                            onMove = {},
                                            onMoveDone = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // While moving an app: a small hint at the bottom.
        if (movingKey != null) {
            MoveHint(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = h * 0.035f)
            )
        }
    }
    }

    menuFor?.let { app ->
        fun close() {
            menuFor = null
        }
        val pkg = app.packageName
        val tileKey = if (menuFromRecent) "recent:" + app.key else app.key
        val actions = buildList {
            add(MenuAction(stringResource(R.string.menu_open), R.drawable.ic_open) {
                close()
                if (app.isFolder) openFolderKey = app.key else vm.launch(app)
            })
            if (!menuFromRecent) {
                add(MenuAction(stringResource(R.string.menu_move), R.drawable.ic_move) {
                    close()
                    movingKey = app.key
                    focusOn(app.key)
                })
                if (visible.firstOrNull()?.key != app.key) {
                    add(MenuAction(stringResource(R.string.menu_move_first), R.drawable.ic_first) {
                        close()
                        reorder(app.key, 0)
                    })
                }
            }
            if (app.isFolder) {
                add(MenuAction(stringResource(R.string.rename), R.drawable.ic_rename) {
                    close()
                    renameFolderKey = app.key
                })
                add(MenuAction(stringResource(R.string.ungroup), R.drawable.ic_remove) {
                    close()
                    vm.ungroup(app.key.removePrefix(FOLDER_PREFIX), dockKeys)
                    focusOn(app.children.firstOrNull()?.key)
                })
            } else if (!menuFromRecent) {
                add(MenuAction(stringResource(R.string.menu_hide), R.drawable.ic_hide) {
                    close()
                    vm.hide(app.key)
                    focusOn(visible.firstOrNull { it.key != app.key }?.key)
                })
                if (pkg != null) {
                    add(MenuAction(stringResource(R.string.add_to_folder), R.drawable.ic_folder) {
                        close()
                        folderPickFor = app
                    })
                }
            }
            if (pkg != null) {
                add(MenuAction(stringResource(R.string.menu_app_info), R.drawable.ic_info) {
                    close()
                    focusOn(tileKey)
                    vm.openAppInfo(pkg)
                })
            }
            if (pkg != null && app.canUninstall) {
                add(MenuAction(stringResource(R.string.menu_uninstall), R.drawable.ic_delete) {
                    close()
                    focusOn(tileKey)
                    vm.uninstall(pkg)
                })
            }
        }
        ActionMenu(
            title = app.label,
            anchor = tileBounds[tileKey],
            actions = actions,
            frost = frost,
            onDismiss = {
                close()
                focusOn(tileKey)
            },
        )
    }

    // "Add to folder": existing folders first, then the ready-made ones not used yet.
    folderPickFor?.let { app ->
        val existing = prefs.folders.keys.toList()
        val choices = existing + FolderPreset.entries.map { it.id }.filter { it !in existing }
        ActionMenu(
            title = stringResource(R.string.add_to_folder),
            anchor = tileBounds[app.key],
            actions = choices.map { id ->
                MenuAction(folderName(context, id, prefs.folderNames), R.drawable.ic_folder) {
                    folderPickFor = null
                    vm.addToFolder(app.key, id, dockKeys)
                    focusOn(FOLDER_PREFIX + id)
                }
            },
            frost = frost,
            onDismiss = {
                folderPickFor = null
                focusOn(app.key)
            },
        )
    }

    openFolder?.let { folder ->
        FolderPanel(
            folder = folder,
            frost = frost,
            onLaunch = {
                openFolderKey = null
                vm.launch(it)
            },
            onLongClick = { folderAppMenu = it },
            onDismiss = {
                openFolderKey = null
                focusOn(folder.key)
            },
        )
    }
    // The folder emptied out (last app removed): close it.
    LaunchedEffect(openFolderKey, openFolder) {
        if (openFolderKey != null && openFolder == null) openFolderKey = null
    }

    folderAppMenu?.let { app ->
        val pkg = app.packageName
        ActionMenu(
            title = app.label,
            anchor = null,
            actions = buildList {
                add(MenuAction(stringResource(R.string.menu_open), R.drawable.ic_open) {
                    folderAppMenu = null
                    openFolderKey = null
                    vm.launch(app)
                })
                add(MenuAction(stringResource(R.string.remove_from_folder), R.drawable.ic_remove) {
                    folderAppMenu = null
                    vm.removeFromFolder(app.key, dockKeys)
                })
                if (pkg != null) {
                    add(MenuAction(stringResource(R.string.menu_app_info), R.drawable.ic_info) {
                        folderAppMenu = null
                        vm.openAppInfo(pkg)
                    })
                }
                if (pkg != null && app.canUninstall) {
                    add(MenuAction(stringResource(R.string.menu_uninstall), R.drawable.ic_delete) {
                        folderAppMenu = null
                        vm.uninstall(pkg)
                    })
                }
            },
            frost = frost,
            onDismiss = { folderAppMenu = null },
        )
    }

    renameFolderKey?.let { key ->
        val folder = visible.firstOrNull { it.key == key }
        RenameDialog(
            current = folder?.label.orEmpty(),
            frost = frost,
            onSave = { name ->
                renameFolderKey = null
                vm.renameFolder(key.removePrefix(FOLDER_PREFIX), name)
                focusOn(key)
            },
            onDismiss = {
                renameFolderKey = null
                focusOn(key)
            },
        )
    }

    if (controlCenterOpen) {
        ControlCenter(
            anchor = ccBounds,
            frost = frost,
            controls = controls,
            theme = prefs.theme,
            onMute = vm::toggleMute,
            onVolume = vm::changeVolume,
            onTheme = { vm.updatePrefs { it.copy(theme = it.theme.next()) } },
            onSleep = { if (!vm.cycleSleepTimer()) runCatching { context.startActivity(TvPower.allowIntent(context)) } },
            onPicture = {
                controlCenterOpen = false
                vm.openPictureSettings()
            },
            onSettings = {
                controlCenterOpen = false
                settingsOpen = true
            },
            onTvSettings = {
                controlCenterOpen = false
                vm.openSystemSettings()
            },
            onWifi = {
                controlCenterOpen = false
                vm.openWifiSettings()
            },
            onScreenOff = {
                if (vm.canTurnOff()) {
                    controlCenterOpen = false
                    vm.screenOff()
                } else {
                    runCatching { context.startActivity(TvPower.allowIntent(context)) }
                }
            },
            onPower = {
                if (vm.powerMenu()) controlCenterOpen = false else toast(R.string.needs_home_service)
            },
            onDismiss = {
                controlCenterOpen = false
                focusOn(focusedKey ?: visible.firstOrNull()?.key)
            },
        )
    }

    if (inputsOpen) {
        InputsPanel(
            anchor = inputsBounds,
            frost = frost,
            sources = sources,
            onPick = {
                inputsOpen = false
                vm.launchSource(it)
            },
            onDismiss = {
                inputsOpen = false
                focusOn(focusedKey ?: visible.firstOrNull()?.key)
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
            frost = frost,
            onHomeSetup = vm::openHomeSetup,
            onChange = vm::updatePrefs,
            onUnhide = vm::unhide,
            onResetOrder = vm::resetOrder,
            onUpdateClick = vm::onUpdateClicked,
            onSystemSettings = vm::openSystemSettings,
            contentAllowed = contentAllowed,
            onAllowContent = onAllowContent,
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
            controlCenterOpen = false
            inputsOpen = false
            openFolderKey = null
            folderPickFor = null
            renameFolderKey = null
            folderAppMenu = null
            backToTop()
        }
    }

    // A launcher has nowhere to go "back" to: jump to the top instead.
    BackHandler(enabled = movingKey == null && !menuOpen) {
        backToTop()
    }
}

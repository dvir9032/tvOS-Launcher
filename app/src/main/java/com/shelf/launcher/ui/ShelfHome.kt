package com.shelf.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.shelf.launcher.data.TvApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COLUMNS = 6

/**
 * The home screen: full-screen artwork of the focused app, its name in the
 * top corner, a clock pill, a glass dock with the first row of apps and the
 * remaining rows peeking out underneath.
 *
 * All sizes are fractions of the screen so it looks the same on any TV density.
 */
@Composable
fun ShelfHome(
    apps: List<TvApp>,
    homePresses: Int,
    onLaunch: (TvApp) -> Unit,
) {
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val focused = apps.firstOrNull { it.key == focusedKey } ?: apps.firstOrNull()
    val rows = remember(apps) { apps.chunked(COLUMNS) }

    val listState = rememberLazyListState()
    val firstTile = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    suspend fun backToTop() {
        listState.scrollToItem(0)
        // The first tile may need a frame or two to attach before it can take focus.
        repeat(10) {
            if (runCatching { firstTile.requestFocus() }.isSuccess) return
            delay(32)
        }
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
        val tileW: Dp = (w - sidePad * 2 - trayPadH * 2 - gap * (COLUMNS - 1)) / COLUMNS
        val tileH: Dp = tileW * 9f / 16f
        val trayTop = h * 0.70f

        Backdrop(app = focused, modifier = Modifier.fillMaxSize())

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
            text = focused?.label.orEmpty(),
            fontSize = w * 0.068f,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = w * 0.046f, top = h * 0.06f)
                .fillMaxWidth(0.6f),
        )

        ClockPill(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = h * 0.048f, end = w * 0.033f)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = trayTop, bottom = h * 0.3f),
            verticalArrangement = Arrangement.spacedBy(h * 0.035f),
        ) {
            itemsIndexed(rows, key = { index, row -> "${index}_${row.first().key}" }) { index, row ->
                val rowContent: @Composable () -> Unit = {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEachIndexed { col, app ->
                            AppTile(
                                app = app,
                                width = tileW,
                                height = tileH,
                                modifier = if (index == 0 && col == 0) Modifier.focusRequester(firstTile) else Modifier,
                                onFocused = { focusedKey = app.key },
                                onClick = { onLaunch(app) },
                            )
                        }
                    }
                }
                if (index == 0) {
                    GlassTray(
                        modifier = Modifier.padding(horizontal = sidePad),
                        padH = trayPadH,
                        padV = trayPadV,
                        content = rowContent,
                    )
                } else {
                    Box(Modifier.padding(horizontal = sidePad + trayPadH)) { rowContent() }
                }
            }
        }
    }

    // Focus the first app when the list first arrives and on every Home press.
    LaunchedEffect(apps.isNotEmpty(), homePresses) {
        if (apps.isNotEmpty()) backToTop()
    }

    // A launcher has nowhere to go "back" to: jump to the top instead.
    BackHandler {
        scope.launch { backToTop() }
    }
}

package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shelf.launcher.data.ShelfPrefs
import com.shelf.launcher.data.TvApp
import com.shelf.launcher.data.next
import com.shelf.launcher.update.UpdateState

private val Panel = Color(0xF0141820)
private val Ink = Color(0xFF0B0E13)

/** A row in a menu or the settings list; white with dark text when focused. */
@Composable
fun MenuButton(
    text: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val color = if (focused) Ink else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.07f), shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            text = text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = color, fontSize = 17.sp, fontWeight = FontWeight.Medium),
        )
        if (value != null) {
            BasicText(
                text = value,
                maxLines = 1,
                style = TextStyle(color = color.copy(alpha = 0.7f), fontSize = 15.sp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp),
        style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
private fun HintText(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp),
    )
}

/** Long-press menu for one app: open, move or hide. */
@Composable
fun ActionMenu(
    app: TvApp,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onHide: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val shape = RoundedCornerShape(26.dp)
            Column(
                Modifier
                    .width(320.dp)
                    .background(Panel, shape)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicText(
                    text = app.label,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                )
                MenuButton("פתח", Modifier.focusRequester(first), onClick = onOpen)
                MenuButton("הזז", onClick = onMove)
                MenuButton("הסתר", onClick = onHide)
            }
        }
    }
    LaunchedEffect(Unit) { first.requestWithRetry() }
}

private fun updateLabel(state: UpdateState, version: String): String = when (state) {
    UpdateState.Idle -> "בדוק · $version"
    UpdateState.Checking -> "בודק…"
    UpdateState.UpToDate -> "מעודכן · $version"
    is UpdateState.Available -> "התקן גרסה ${state.build}"
    is UpdateState.Downloading -> "מוריד ${(state.progress * 100).toInt()}%"
    UpdateState.Installing -> "מתקין…"
    is UpdateState.Failed -> state.reason
}

/** Launcher settings, sliding in from the right. */
@Composable
fun SettingsPanel(
    prefs: ShelfPrefs,
    hiddenApps: List<TvApp>,
    hasSources: Boolean,
    update: UpdateState,
    versionName: String,
    onChange: ((ShelfPrefs) -> ShelfPrefs) -> Unit,
    onUnhide: (String) -> Unit,
    onResetOrder: () -> Unit,
    onUpdateClick: () -> Unit,
    onSystemSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .align(Alignment.CenterStart) // the right edge, in RTL
                        .fillMaxHeight()
                        .fillMaxWidth(0.42f)
                        .background(Panel)
                        .padding(horizontal = 28.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicText(
                        text = "הגדרות",
                        modifier = Modifier.padding(start = 4.dp),
                        style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { SectionLabel("מראה") }
                        item {
                            MenuButton("אפליקציות בשורה", Modifier.focusRequester(first), value = "${prefs.columns}") {
                                onChange { it.copy(columns = if (it.columns >= 7) 5 else it.columns + 1) }
                            }
                        }
                        item {
                            MenuButton("שעון", value = prefs.clock.label) { onChange { it.copy(clock = it.clock.next()) } }
                        }
                        item {
                            MenuButton("זכוכית", value = prefs.glass.label) { onChange { it.copy(glass = it.glass.next()) } }
                        }
                        item {
                            MenuButton("רקע", value = prefs.backdrop.label) { onChange { it.copy(backdrop = it.backdrop.next()) } }
                        }
                        if (hasSources) {
                            item {
                                MenuButton("שורת מקורות HDMI", value = if (prefs.showInputs) "מוצגת" else "מוסתרת") {
                                    onChange { it.copy(showInputs = !it.showInputs) }
                                }
                            }
                        }

                        item { SectionLabel("אפליקציות") }
                        item { MenuButton("איפוס סדר", value = "א–ת", onClick = onResetOrder) }
                        if (hiddenApps.isEmpty()) {
                            item { HintText("אין אפליקציות מוסתרות. לחיצה ארוכה על OK מעל אפליקציה פותחת תפריט להזזה ולהסתרה.") }
                        } else {
                            items(hiddenApps, key = { it.key }) { app ->
                                MenuButton(app.label, value = "הצג") { onUnhide(app.key) }
                            }
                        }

                        item { SectionLabel("מערכת") }
                        item { MenuButton("עדכונים", value = updateLabel(update, versionName), onClick = onUpdateClick) }
                        item { MenuButton("הגדרות הטלוויזיה", onClick = onSystemSettings) }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { first.requestWithRetry() }
}

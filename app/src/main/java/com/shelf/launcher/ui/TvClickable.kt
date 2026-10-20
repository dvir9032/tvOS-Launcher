package com.shelf.launcher.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay

private fun isSelectKey(code: Int) =
    code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        code == AndroidKeyEvent.KEYCODE_ENTER ||
        code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER

/**
 * Remote-friendly click: OK clicks on release, holding OK (or the Menu button)
 * fires [onLongClick]. Makes the element focusable.
 */
fun Modifier.tvClickable(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    // [0] = OK went down on this element, [1] = long press already fired
    val press = remember { booleanArrayOf(false, false) }
    val fx = LocalFocusFx.current
    this
        // Arrows are seen here first (also inside menus and dialogs), so the
        // element that gains focus next knows the remote moved it.
        .onPreviewKeyEvent { fx.onKey(it.nativeKeyEvent); false }
        .onKeyEvent { event ->
            val native = event.nativeKeyEvent
            val code = native.keyCode
            when {
                isSelectKey(code) && event.type == KeyEventType.KeyDown -> {
                    if (native.repeatCount == 0) {
                        press[0] = true
                        press[1] = false
                    } else if (onLongClick != null && press[0] && !press[1] &&
                        (native.isLongPress || native.repeatCount >= 8)
                    ) {
                        press[1] = true
                        onLongClick()
                    }
                    true
                }
                isSelectKey(code) && event.type == KeyEventType.KeyUp -> {
                    val fire = press[0] && !press[1]
                    press[0] = false
                    press[1] = false
                    if (fire) onClick()
                    true
                }
                code == AndroidKeyEvent.KEYCODE_MENU && onLongClick != null -> {
                    if (event.type == KeyEventType.KeyUp) onLongClick()
                    true
                }
                else -> false
            }
        }
        .onFocusChanged { if (it.isFocused) fx.onFocusMoved() }
        .focusable()
}

/** A freshly shown element may need a frame or two before it can take focus. */
suspend fun FocusRequester.requestWithRetry(times: Int = 15) {
    repeat(times) {
        if (runCatching { requestFocus() }.isSuccess) return
        delay(32)
    }
}

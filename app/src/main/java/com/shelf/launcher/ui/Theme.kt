package com.shelf.launcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Colors of Shelf's glass: the dock, top bar, menus and panels.
 * Text over the big picture (titles) stays white in both looks.
 *
 * Dark: dark frosted glass, white text; the focused item turns white.
 * Light: bright frosted glass, dark text; the focused item turns dark.
 */
data class ShelfColors(
    val dark: Boolean,
    /** laid over the blurred screen inside glass panels */
    val frostOverlay: Color,
    /** glass panels before the blur is ready (or if it fails) */
    val panelFallback: Brush,
    val rim: Brush,
    val text: Color,
    val subText: Color,
    val focusFill: Color,
    val focusText: Color,
    /** tiles, circles and bars inside panels when not focused */
    val chip: Color,
    /** small round icon backgrounds inside rows */
    val badge: Color,
    /** round buttons and pills in the top bar */
    val pill: Color,
    val pillText: Color,
    /** how much white the dock glass gets, multiplied by the "Glass" setting */
    val dockBoost: Float,
)

private val Ink = Color(0xFF0B0E13)
private val LightInk = Color(0xFF14151B)

val DarkColors = ShelfColors(
    dark = true,
    frostOverlay = Color.Black.copy(alpha = 0.32f),
    panelFallback = Brush.verticalGradient(listOf(Color(0xF02C313B), Color(0xF31A1D24))),
    rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.06f))),
    text = Color.White,
    subText = Color.White.copy(alpha = 0.6f),
    focusFill = Color.White,
    focusText = Ink,
    chip = Color.White.copy(alpha = 0.12f),
    badge = Color.White.copy(alpha = 0.16f),
    pill = Color.White.copy(alpha = 0.16f),
    pillText = Color.White,
    dockBoost = 1f,
)

val LightColors = ShelfColors(
    dark = false,
    frostOverlay = Color.White.copy(alpha = 0.55f),
    panelFallback = Brush.verticalGradient(listOf(Color(0xF2F3F4F8), Color(0xF5E6E8EE))),
    rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.4f))),
    text = LightInk,
    subText = LightInk.copy(alpha = 0.62f),
    focusFill = LightInk,
    focusText = Color.White,
    chip = Color.White.copy(alpha = 0.45f),
    badge = LightInk.copy(alpha = 0.08f),
    pill = Color.White.copy(alpha = 0.72f),
    pillText = LightInk,
    dockBoost = 2.6f,
)

val LocalShelfColors = staticCompositionLocalOf { DarkColors }

package com.shelf.launcher.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.shelf.launcher.R

/**
 * Heebo (SIL Open Font License, see docs/FONT-LICENSE-Heebo.txt): one family
 * for Hebrew and Latin, so titles and menus look the same in both languages.
 */
val Heebo = FontFamily(
    Font(R.font.heebo_regular, FontWeight.Normal),
    Font(R.font.heebo_medium, FontWeight.Medium),
    Font(R.font.heebo_semibold, FontWeight.SemiBold),
    Font(R.font.heebo_bold, FontWeight.Bold),
    Font(R.font.heebo_extrabold, FontWeight.ExtraBold),
)

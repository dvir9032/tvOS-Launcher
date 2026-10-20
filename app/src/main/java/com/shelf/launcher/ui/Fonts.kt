package com.shelf.launcher.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.shelf.launcher.R

/**
 * The one typeface used everywhere in Shelf: Assistant (SIL Open Font License,
 * see docs/FONT-LICENSE-Assistant.txt). It covers Hebrew and Latin, so titles
 * and menus look the same in both languages. To change fonts, replace the files
 * in res/font and this list.
 */
val ShelfFont = FontFamily(
    Font(R.font.assistant_regular, FontWeight.Normal),
    Font(R.font.assistant_medium, FontWeight.Medium),
    Font(R.font.assistant_semibold, FontWeight.SemiBold),
    Font(R.font.assistant_bold, FontWeight.Bold),
    Font(R.font.assistant_extrabold, FontWeight.ExtraBold),
)

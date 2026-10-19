package com.shelf.launcher.data

import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/**
 * One launchable entry in the dock.
 *
 * @param banner   the app's 16:9 TV banner, if it has one (most TV apps do)
 * @param icon     fallback square icon for apps without a banner
 * @param backdrop a tiny copy of the banner; drawn full-screen with bilinear
 *                 filtering it becomes a soft blurred background (real blur
 *                 effects need Android 12, this TV runs Android 10)
 * @param tint     main color pulled from the artwork, used for gradients
 */
data class TvApp(
    val key: String,
    val label: String,
    val intent: Intent,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val backdrop: ImageBitmap?,
    val tint: Color,
    val tintDark: Color,
)

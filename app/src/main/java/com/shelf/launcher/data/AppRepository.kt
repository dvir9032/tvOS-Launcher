package com.shelf.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.shelf.launcher.R

/** Reads the installed apps from the system. Call [load] off the main thread. */
class AppRepository(private val context: Context) {

    private val pm = context.packageManager

    fun load(): List<TvApp> {
        val found = LinkedHashMap<String, TvApp>()
        // TV apps first, so their banner and TV entry point win.
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, found)
        // Phone-style apps that were sideloaded and have no TV entry point.
        collect(Intent.CATEGORY_LAUNCHER, found)

        val apps = found.values.sortedBy { it.label.lowercase() }
        return apps + settingsEntry()
    }

    private fun collect(category: String, into: MutableMap<String, TvApp>) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(category)
        @Suppress("DEPRECATION")
        val results = pm.queryIntentActivities(query, 0)
        for (info in results) {
            val activity = info.activityInfo ?: continue
            val pkg = activity.packageName
            if (pkg == context.packageName || into.containsKey(pkg)) continue

            val launch = Intent(Intent.ACTION_MAIN)
                .addCategory(category)
                .setComponent(ComponentName(pkg, activity.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

            val banner = runCatching {
                activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
            }.getOrNull()
            val icon = runCatching { info.loadIcon(pm) }.getOrNull()

            val isSystem = (activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            into[pkg] = build(
                key = pkg,
                canUninstall = !isSystem,
                label = info.loadLabel(pm).toString(),
                intent = launch,
                bannerDrawable = banner,
                iconDrawable = icon,
            )
        }
    }

    private fun settingsEntry(): TvApp {
        val icon = ContextCompat.getDrawable(context, R.drawable.ic_settings)
            ?.let { toSoftwareBitmap(it, 160, 160) }
        return TvApp(
            key = "shelf:settings",
            label = context.getString(R.string.settings),
            packageName = null,
            canUninstall = false,
            intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            banner = null,
            icon = icon?.asImageBitmap(),
            backdrop = null,
            tint = Color(0xFF5B6578),
            tintDark = Color(0xFF242A35),
        )
    }

    private fun build(
        key: String,
        canUninstall: Boolean,
        label: String,
        intent: Intent,
        bannerDrawable: Drawable?,
        iconDrawable: Drawable?,
    ): TvApp {
        val banner = bannerDrawable?.let { toSoftwareBitmap(it, 320, 180) }
        val icon = if (banner == null) iconDrawable?.let { toSoftwareBitmap(it, 192, 192) } else null

        val source = banner ?: icon
        val palette = source?.let { runCatching { Palette.from(it).maximumColorCount(16).generate() }.getOrNull() }
        val fallback = 0xFF2C3A55.toInt()
        val main = palette?.let { it.getVibrantColor(it.getDominantColor(fallback)) } ?: fallback
        val dark = palette?.let { it.getDarkMutedColor(it.getDarkVibrantColor(darken(main))) } ?: darken(main)

        // 20x12 pixels stretched over the whole screen = a free, smooth "blur".
        val backdrop = banner?.let { Bitmap.createScaledBitmap(it, 20, 12, true) }

        return TvApp(
            key = key,
            label = label,
            packageName = key,
            canUninstall = canUninstall,
            intent = intent,
            banner = banner?.asImageBitmap(),
            icon = icon?.asImageBitmap(),
            backdrop = backdrop?.asImageBitmap(),
            tint = Color(main),
            tintDark = Color(dark),
        )
    }

    private fun toSoftwareBitmap(drawable: Drawable, width: Int, height: Int): Bitmap? = runCatching {
        val bmp = drawable.toBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
    }.getOrNull()

    private fun darken(color: Int): Int = ColorUtils.blendARGB(color, 0xFF000000.toInt(), 0.65f)
}

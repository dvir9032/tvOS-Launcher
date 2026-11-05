package com.shelf.launcher.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Shelf's own language, separate from the TV's. Android TV 10 has no per-app
 * language setting, so Shelf applies the chosen one to its own screens itself.
 */
object Languages {

    /** Language tag ("" = follow the TV) → its name, written in that language. */
    val ALL: List<Pair<String, String>> = listOf(
        "" to "",
        "en" to "English",
        "iw" to "עברית",
        "ar" to "العربية",
        "ru" to "Русский",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "pt" to "Português",
        "it" to "Italiano",
        "tr" to "Türkçe",
    )

    const val PREF = "language"

    fun nameOf(tag: String): String = ALL.firstOrNull { it.first == tag }?.second.orEmpty()

    fun saved(context: Context): String =
        context.getSharedPreferences("shelf", Context.MODE_PRIVATE).getString(PREF, "").orEmpty()

    /** [base] with Shelf's chosen language (and its text direction), or as is when following the TV. */
    fun wrap(base: Context): Context {
        val tag = saved(base)
        if (tag.isEmpty()) {
            // Back to the TV's language (a language picked earlier may still be the default).
            Locale.setDefault(android.content.res.Resources.getSystem().configuration.locales[0])
            return base
        }
        val locale = Locale(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale) // also sets the layout direction (right to left for Hebrew, Arabic)
        return base.createConfigurationContext(config)
    }
}

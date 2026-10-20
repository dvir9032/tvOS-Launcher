package com.shelf.launcher.data

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.shelf.launcher.R

/**
 * Ready-made folders (no typing with the remote needed). Any folder can be
 * renamed later; renamed or extra folders keep their own id.
 */
enum class FolderPreset(val id: String, @StringRes val label: Int) {
    GAMES("games", R.string.folder_games),
    MOVIES("movies", R.string.folder_movies),
    MUSIC("music", R.string.folder_music),
    KIDS("kids", R.string.folder_kids),
    TOOLS("tools", R.string.folder_tools),
}

fun folderName(context: Context, id: String, names: Map<String, String>): String =
    names[id] ?: FolderPreset.entries.firstOrNull { it.id == id }?.let { context.getString(it.label) } ?: id

/**
 * The home screen's tiles: apps that aren't in a folder, plus one tile per
 * non-empty folder, in the saved order.
 */
fun buildDock(context: Context, apps: List<TvApp>, prefs: ShelfPrefs): List<TvApp> {
    val byKey = apps.associateBy { it.key }
    val inFolders = prefs.folders.values.flatten().toSet()
    val folderTiles = prefs.folders.mapNotNull { (id, keys) ->
        val children = keys.mapNotNull { byKey[it] }
        if (children.isEmpty()) null else TvApp(
            key = FOLDER_PREFIX + id,
            label = folderName(context, id, prefs.folderNames),
            packageName = null,
            canUninstall = false,
            intent = Intent(),
            banner = null,
            icon = null,
            backdrop = null,
            tint = Color(0xFF3A4252),
            tintDark = Color(0xFF1A1E26),
            children = children,
        )
    }
    return arrange(apps.filter { it.key !in inFolders } + folderTiles, prefs.order)
}

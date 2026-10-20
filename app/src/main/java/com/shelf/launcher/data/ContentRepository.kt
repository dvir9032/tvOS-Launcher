package com.shelf.launcher.data

import android.content.Context
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.provider.BaseColumns
import android.util.Log
import androidx.core.content.ContextCompat

/** One show, episode or video that an app put on the TV's home screen. */
data class ShelfItem(
    val id: Long,
    val packageName: String,
    val title: String,
    val description: String?,
    val image: String?,
    val intentUri: String?,
)

/**
 * Reads the content apps publish to the Android TV home screen (preview channels).
 *
 * The TV only shares this with launchers that hold the "TV listings" permission,
 * and then only programs the app marked as searchable. If it isn't allowed, or the
 * TV doesn't have this, the result is simply empty.
 */
class ContentRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Content per app package, best first. Call off the main thread. */
    fun load(): Map<String, List<ShelfItem>> {
        if (!hasPermission()) return emptyMap()
        val projection = arrayOf(
            BaseColumns._ID,
            TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
            TvContract.PreviewPrograms.COLUMN_TITLE,
            TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
            TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
            TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
            TvContract.PreviewPrograms.COLUMN_INTENT_URI,
            TvContract.PreviewPrograms.COLUMN_BROWSABLE,
            TvContract.PreviewPrograms.COLUMN_WEIGHT,
        )
        val result = runCatching {
            val rows = LinkedHashMap<String, MutableList<Pair<Int, ShelfItem>>>()
            context.contentResolver.query(TvContract.PreviewPrograms.CONTENT_URI, projection, null, null, null)
                ?.use { c ->
                    while (c.moveToNext()) {
                        val pkg = c.getString(1) ?: continue
                        val title = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                        val browsable = c.isNull(7) || c.getInt(7) != 0
                        if (!browsable) continue
                        val item = ShelfItem(
                            id = c.getLong(0),
                            packageName = pkg,
                            title = title,
                            description = c.getString(3)?.takeIf { it.isNotBlank() },
                            image = c.getString(5)?.takeIf { it.isNotBlank() } ?: c.getString(4),
                            intentUri = c.getString(6),
                        )
                        rows.getOrPut(pkg) { mutableListOf() }.add((if (c.isNull(8)) 0 else c.getInt(8)) to item)
                    }
                }
            rows.mapValues { (_, items) ->
                items.sortedByDescending { it.first }.map { it.second }.distinctBy { it.title }.take(MAX_PER_APP)
            }
        }.onFailure { Log.w(TAG, "Can't read app content", it) }.getOrDefault(emptyMap())
        Log.i(TAG, "App content: " + result.mapValues { it.value.size })
        return result
    }

    companion object {
        // Not in the public SDK as a constant, but a normal runtime permission the user can allow.
        const val PERMISSION = "android.permission.READ_TV_LISTINGS"
        private const val TAG = "ShelfContent"
        private const val MAX_PER_APP = 10
    }
}

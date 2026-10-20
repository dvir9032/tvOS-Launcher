package com.shelf.launcher.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Loads artwork from the web or from another app, scaled down, with a small memory cache. */
object ArtLoader {

    private val cache = LruCache<String, ImageBitmap>(48)

    suspend fun load(context: Context, uri: String, maxWidth: Int): ImageBitmap? {
        val key = "$maxWidth|$uri"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = open(context, uri).use { it.readBytes() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }

    private fun open(context: Context, uri: String): InputStream =
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            (URL(uri).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ShelfLauncher")
            }.inputStream
        } else {
            context.contentResolver.openInputStream(Uri.parse(uri)) ?: error("No image at $uri")
        }
}

/** The picture at [uri], or null while it loads or if it can't be loaded. */
@Composable
fun rememberArt(uri: String?, maxWidth: Int): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri, maxWidth) {
        value = uri?.let { ArtLoader.load(context, it, maxWidth) }
    }
}

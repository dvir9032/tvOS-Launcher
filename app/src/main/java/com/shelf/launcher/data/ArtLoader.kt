package com.shelf.launcher.data

import android.content.Context
import android.graphics.Bitmap
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

/**
 * Loads artwork from the web or from another app, scaled down, with a small memory cache.
 *
 * The cache is counted in bytes, not pictures, and capped at a sixth of the app's memory: a top shelf
 * picture is several MB, and on a TV with 1 GB of RAM a fixed number of them could push Shelf (and
 * the app being watched) out of memory. MainActivity empties it when Android asks for memory back
 * while Shelf is in the background.
 */
object ArtLoader {

    private val cache = object : LruCache<String, ImageBitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    private fun cacheBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 6).coerceIn(8L * 1024 * 1024, 64L * 1024 * 1024).toInt()

    /** Drops every cached picture; they load again when they're next shown. */
    fun trim() = cache.evictAll()

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
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { fitWidth(it, maxWidth) }?.asImageBitmap()
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }

    /** Sampling only halves, so a 1920-wide picture asked for at 1280 stays 1920: scale it the rest of the way. */
    private fun fitWidth(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val height = (bitmap.height.toLong() * maxWidth / bitmap.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, maxWidth, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
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

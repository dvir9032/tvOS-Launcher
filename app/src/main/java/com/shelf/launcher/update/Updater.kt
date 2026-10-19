package com.shelf.launcher.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.shelf.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val build: Int, val url: String) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * Checks the GitHub releases of this project for a newer build, downloads the APK
 * and hands it to Android's installer. Release tags look like "build-12" and
 * versionCode is 1 + the build number, so the two can be compared directly.
 */
class Updater(private val context: Context) {

    val currentBuild: Int get() = BuildConfig.VERSION_CODE - 1

    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (conn.responseCode != 200) return@runCatching UpdateState.Failed("GitHub ${conn.responseCode}")
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val build = json.optString("tag_name").removePrefix("build-").toIntOrNull()
                    ?: return@runCatching UpdateState.UpToDate
                val assets = json.optJSONArray("assets")
                var url: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name").endsWith(".apk")) {
                            url = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
                if (url != null && build > currentBuild) UpdateState.Available(build, url) else UpdateState.UpToDate
            } finally {
                conn.disconnect()
            }
        }.getOrElse { UpdateState.Failed("אין חיבור לרשת") }
    }

    suspend fun downloadAndInstall(url: String, onProgress: (Float) -> Unit): UpdateState =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(context.cacheDir, "update.apk")
                val conn = open(url)
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        file.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                done += n
                                if (total > 0) onProgress(done.toFloat() / total)
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                install(file)
                UpdateState.Installing
            }.getOrElse { UpdateState.Failed(it.message ?: "ההורדה נכשלה") }
        }

    private fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("shelf.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallResultReceiver::class.java), flags,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ShelfLauncher")
        }
}

package com.shelf.launcher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The feel of the remote: a soft tick when focus moves, which way it moved
 * (tiles lean into the move), and whether the remote has been left alone
 * (everything but the focused tile dims a little).
 */
@Stable
class FocusFx(private val sound: TickSound?) {
    var soundOn = true

    /** Last arrow pressed: -1, 0 or 1 on each axis. */
    var dirX = 0
        private set
    var dirY = 0
        private set

    private var lastKeyAt = 0L

    /** Bumped on every key press; the idle timer restarts from it. */
    var touches by mutableLongStateOf(0L)
        private set

    /** 0 while the remote is in use, 1 a few seconds after the last press. */
    val idle = Animatable(0f)

    fun onKey(event: AndroidKeyEvent) {
        if (event.action != AndroidKeyEvent.ACTION_DOWN) return
        touches++
        val (x, y) = when (event.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> -1 to 0
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> 1 to 0
            AndroidKeyEvent.KEYCODE_DPAD_UP -> 0 to -1
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> 0 to 1
            else -> return
        }
        dirX = x
        dirY = y
        lastKeyAt = SystemClock.uptimeMillis()
    }

    /** True when focus is moving because the user just pressed an arrow. */
    fun movedByRemote(): Boolean = SystemClock.uptimeMillis() - lastKeyAt < 300

    /** Called when something gains focus. */
    fun onFocusMoved() {
        if (soundOn && movedByRemote()) sound?.play()
    }
}

val LocalFocusFx = staticCompositionLocalOf { FocusFx(null) }

private const val IDLE_AFTER_MS = 6_000L

@Composable
fun rememberFocusFx(soundOn: Boolean): FocusFx {
    val context = LocalContext.current
    val sound = remember { runCatching { TickSound(context.applicationContext) }.getOrNull() }
    DisposableEffect(sound) { onDispose { sound?.release() } }
    val fx = remember { FocusFx(sound) }
    fx.soundOn = soundOn
    LaunchedEffect(fx.touches) {
        fx.idle.animateTo(0f, tween(150))
        delay(IDLE_AFTER_MS)
        fx.idle.animateTo(1f, tween(900))
    }
    return fx
}

/**
 * A short, quiet tick made in code (no sound file): a fast-fading high tone with
 * a little low body, played through SoundPool so it keeps up with a held arrow.
 */
class TickSound(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private var loaded = false
    private val id: Int
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        val file = File(context.cacheDir, "shelf-tick-v1.wav")
        if (!file.exists()) file.writeBytes(wav(tick()))
        pool.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
        id = pool.load(file.path, 1)
    }

    fun play() {
        if (!loaded) return
        // Silent when the TV is muted or turned all the way down.
        val muted = runCatching {
            audio.isStreamMute(AudioManager.STREAM_MUSIC) || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }.getOrDefault(false)
        if (!muted) pool.play(id, 0.32f, 0.32f, 1, 0, 1f)
    }

    fun release() = pool.release()

    private fun tick(): ShortArray {
        val rate = SAMPLE_RATE
        val n = rate * 18 / 1000 // 18 ms
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val click = sin(2 * PI * 3200 * t) * exp(-t * 900)
            val body = sin(2 * PI * 520 * t) * exp(-t * 320) * 0.45
            val attack = (i / (rate * 0.0006)).coerceAtMost(1.0) // no pop at the start
            ((click + body) * attack * 0.6 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun wav(samples: ShortArray): ByteArray {
        val data = samples.size * 2
        return ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data)
            samples.forEach { putShort(it) }
        }.array()
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
    }
}

package com.shelf.launcher.dream

import android.animation.ArgbEvaluator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.shelf.launcher.R
import com.shelf.launcher.data.Prefs
import com.shelf.launcher.ui.formatTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Shelf's screensaver: the time and date in Shelf's font on black, over a slow colored glow.
 *
 * Every minute the clock drifts to a new spot and the glow shifts color, so nothing stays in one
 * place long enough to burn into the screen. No pictures and no network, so it costs almost nothing
 * on a TV with little memory. Any key on the remote wakes the TV back to where it was.
 *
 * Chosen in the TV's screensaver settings (Settings → Appearance → Screensaver in Shelf opens them),
 * or over ADB:
 *   adb shell settings put secure screensaver_components com.shelf.launcher/.dream.ClockDream
 *
 * The idea comes from CleanYourTV's clock screensaver (MIT, github.com/Gathin23/cleanyourtv).
 */
class ClockDream : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private val glow = GradientDrawable().apply { gradientType = GradientDrawable.RADIAL_GRADIENT }
    private lateinit var root: FrameLayout
    private lateinit var block: LinearLayout
    private lateinit var time: TextView
    private lateinit var date: TextView
    private var colorIndex = Random.nextInt(PALETTE.size)
    private var glowColor = PALETTE[colorIndex]

    private val tick = object : Runnable {
        override fun run() {
            showTime()
            moveClock()
            shiftGlow()
            handler.postDelayed(this, millisToNextMinute())
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false // any key ends the screensaver
        isFullscreen = true
        isScreenBright = false

        val font = runCatching { resources.getFont(R.font.assistant_regular) }.getOrNull()
        val light = runCatching { resources.getFont(R.font.assistant_medium) }.getOrNull()

        time = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 112f)
            typeface = font
            includeFontPadding = false
            gravity = Gravity.CENTER
            setShadowLayer(30f, 0f, 6f, 0x73000000)
        }
        date = TextView(this).apply {
            setTextColor(0xB3FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = light
            gravity = Gravity.CENTER
        }
        block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(time)
            addView(date)
            alpha = 0f
        }
        val ambient = View(this).apply { background = glow }
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(ambient, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            // Pinned to the top left in every language: the position is set with translation, and in
            // Hebrew a default START gravity would put it at the right edge, pushing it off screen.
            addView(
                block,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.LEFT,
                ),
            )
            addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ -> glow.gradientRadius = (r - l) * 0.55f }
        }
        setGlow(glowColor)
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        showTime()
        block.post { moveClock() } // also fades the clock in once it has a place
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, millisToNextMinute())
    }

    override fun onDreamingStopped() {
        stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun stop() {
        handler.removeCallbacksAndMessages(null)
        if (::block.isInitialized) block.animate().cancel()
    }

    private fun showTime() {
        val mode = runCatching { Prefs(this).state.value.clock }.getOrNull()
        time.text = if (mode != null) formatTime(this, mode) else DateFormat.getTimeFormat(this).format(Date())
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        date.text = DateFormat.format(pattern, Date())
    }

    /** Somewhere new on the screen, keeping clear of the edges TVs crop (overscan). */
    private fun moveClock(retries: Int = 5) {
        val w = root.width - block.width
        val h = root.height - block.height
        if (w <= 0 || h <= 0) {
            // Not laid out yet: try again on the next frame.
            if (retries > 0) root.post { moveClock(retries - 1) } else fadeIn()
            return
        }
        val marginX = root.width * 0.06f
        val marginY = root.height * 0.08f
        val x = marginX + Random.nextFloat() * (w - 2 * marginX).coerceAtLeast(0f)
        val y = marginY + Random.nextFloat() * (h - 2 * marginY).coerceAtLeast(0f)
        if (block.alpha == 0f) {
            block.translationX = x
            block.translationY = y
            fadeIn()
            return
        }
        // Fade out, move, fade in: a slide across the screen would draw the eye.
        block.animate().alpha(0f).setDuration(900).withEndAction {
            block.translationX = x
            block.translationY = y
            block.animate().alpha(1f).setDuration(900).start()
        }.start()
    }

    private fun fadeIn() {
        block.animate().alpha(1f).setDuration(1200).start()
    }

    /**
     * The next color over 8 seconds in 16 steps. Animating every frame would redraw the full-screen
     * gradient about 480 times for a change nobody can see that finely.
     */
    private fun shiftGlow() {
        val from = glowColor
        colorIndex = (colorIndex + 1) % PALETTE.size
        val to = PALETTE[colorIndex]
        val evaluator = ArgbEvaluator()
        for (step in 1..GLOW_STEPS) {
            handler.postDelayed({ setGlow(evaluator.evaluate(step / GLOW_STEPS.toFloat(), from, to) as Int) }, step * GLOW_STEP_MS)
        }
    }

    private fun setGlow(color: Int) {
        glowColor = color
        // Deep colors at low strength on black: a soft light in the room, not a bright screen.
        val center = (color and 0x00FFFFFF) or (0x59 shl 24)
        glow.colors = intArrayOf(center, Color.TRANSPARENT)
    }

    private fun millisToNextMinute(): Long {
        val now = Calendar.getInstance()
        return (60 - now.get(Calendar.SECOND)) * 1000L - now.get(Calendar.MILLISECOND) + 50
    }

    private companion object {
        val PALETTE = intArrayOf(0xFF2B5CFF.toInt(), 0xFF00A39A.toInt(), 0xFF8A3FFC.toInt(), 0xFFE0661B.toInt(), 0xFFD6336C.toInt())
        const val GLOW_STEPS = 16
        const val GLOW_STEP_MS = 500L
    }
}

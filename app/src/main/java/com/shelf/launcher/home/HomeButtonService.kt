package com.shelf.launcher.home

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.shelf.launcher.MainActivity

/**
 * For TVs that won't let you pick another home screen (Sony, many Google TV devices):
 * as soon as the built-in launcher comes to the front, Shelf opens over it.
 *
 * It only watches which app's window is in front; it reads no screen content.
 * Turn it on in Settings → Device Preferences → Accessibility → Shelf.
 */
class HomeButtonService : AccessibilityService() {

    private var builtInLaunchers: Set<String> = emptySet()
    private var lastLaunch = 0L

    override fun onServiceConnected() {
        builtInLaunchers = otherLaunchers(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in builtInLaunchers) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < 700) return
        lastLaunch = now

        // Same intent the Home button sends, so Shelf also jumps back to the first app.
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
    }

    override fun onInterrupt() = Unit

    companion object {
        // Settings apps also register a fallback home screen; never take over those.
        private val NEVER_REPLACE = setOf("com.android.settings", "com.android.tv.settings")

        /** Every other installed home screen (the TV's own launcher). */
        fun otherLaunchers(context: Context): Set<String> {
            val pm = context.packageManager
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            val found = pm.queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)
            return found.mapNotNull { it.activityInfo?.packageName }
                .filter { it != context.packageName && it !in NEVER_REPLACE }
                .toSet()
        }

        /** Is Shelf already what the Home button opens on its own? */
        fun isDefaultHome(context: Context): Boolean {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            return resolved?.activityInfo?.packageName == context.packageName
        }

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it)?.packageName == context.packageName
            }
        }
    }
}

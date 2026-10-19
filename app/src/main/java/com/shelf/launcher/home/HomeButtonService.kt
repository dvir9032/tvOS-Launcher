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
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * For TVs that won't let you pick another home screen (Sony, many Google TV devices):
 * as soon as the built-in launcher comes to the front, Shelf opens over it.
 *
 * It only watches which app's window is in front; it reads no screen content.
 * Turn it on in Settings → Device Preferences → Accessibility → Shelf.
 */
class HomeButtonService : AccessibilityService() {

    private var builtInLaunchers: Set<String> = emptySet()
    private var launcherActivities: Set<String> = emptySet()
    private var lastLaunch = 0L

    override fun onServiceConnected() {
        builtInLaunchers = otherLaunchers(this)
        launcherActivities = otherLauncherActivities(this)
        running.value = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        running.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        running.value = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in builtInLaunchers) return

        // Only the launcher's own screen coming up counts, not a popup or view inside it.
        val cls = event.className?.toString()
        if (cls != null && cls !in launcherActivities && cls.startsWith("android.")) return

        val now = SystemClock.elapsedRealtime()
        // Shelf just opened an app: the built-in launcher can flash by during that
        // transition, and taking over then would cover the app the user picked.
        if (now < quietUntil) return
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

        /** True while the service is switched on and connected; Shelf watches this live. */
        val running = MutableStateFlow(false)

        /** Elapsed-realtime until which the service stays out of the way. */
        @Volatile
        private var quietUntil = 0L

        /** Called by Shelf right before it opens an app. */
        fun stayQuiet(millis: Long = 3000) {
            quietUntil = SystemClock.elapsedRealtime() + millis
        }

        private fun homeActivities(context: Context) = run {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)
                .mapNotNull { it.activityInfo }
                .filter { it.packageName != context.packageName && it.packageName !in NEVER_REPLACE }
        }

        private fun otherLauncherActivities(context: Context): Set<String> =
            homeActivities(context).map { it.targetActivity ?: it.name }.toSet() +
                homeActivities(context).map { it.name }.toSet()

        /** Every other installed home screen (the TV's own launcher). */
        fun otherLaunchers(context: Context): Set<String> =
            homeActivities(context).map { it.packageName }.toSet()

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

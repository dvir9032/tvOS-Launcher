package com.shelf.launcher.home

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shelf.launcher.R

/**
 * Lets Shelf turn the TV's screen off: needs the user to allow Shelf as a device
 * admin once (only the "lock the screen" right). Used by "Screen off" and the
 * sleep timer in Control Center.
 */
class ShelfAdmin : DeviceAdminReceiver()

/** Fires when the sleep timer runs out. */
class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TvPower.clearSleepTimer(context)
        TvPower.screenOff(context)
    }
}

object TvPower {

    val SLEEP_CHOICES = listOf(0, 15, 30, 60, 90, 120)

    private fun admin(context: Context) = ComponentName(context, ShelfAdmin::class.java)
    private fun dpm(context: Context) = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun canTurnOff(context: Context): Boolean = runCatching { dpm(context).isAdminActive(admin(context)) }.getOrDefault(false)

    /** The system screen that asks to allow Shelf to turn the screen off. */
    fun allowIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.admin_explain))

    /** Turns the screen off (the TV goes to standby). False if not allowed. */
    fun screenOff(context: Context): Boolean = runCatching {
        if (!canTurnOff(context)) return false
        dpm(context).lockNow()
        true
    }.getOrDefault(false)

    /** The TV's own power menu (restart, turn off), through the Home button service. */
    fun powerMenu(): Boolean = HomeButtonService.powerMenu()

    private fun sleepIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 7, Intent(context, SleepReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Minutes left on the sleep timer, or 0 when it's off. */
    fun sleepMinutesLeft(context: Context): Int {
        val end = context.getSharedPreferences("shelf", Context.MODE_PRIVATE).getLong("sleepAt", 0L)
        val left = end - System.currentTimeMillis()
        return if (left <= 0) 0 else ((left + 59_999) / 60_000).toInt()
    }

    /** Starts (or with 0, cancels) the sleep timer. */
    fun setSleepTimer(context: Context, minutes: Int) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(sleepIntent(context))
        if (minutes <= 0) {
            clearSleepTimer(context)
            return
        }
        val at = System.currentTimeMillis() + minutes * 60_000L
        context.getSharedPreferences("shelf", Context.MODE_PRIVATE).edit().putLong("sleepAt", at).apply()
        if (Build.VERSION.SDK_INT < 31) {
            alarms.setExact(AlarmManager.RTC_WAKEUP, at, sleepIntent(context))
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000L, sleepIntent(context))
        }
    }

    fun clearSleepTimer(context: Context) {
        context.getSharedPreferences("shelf", Context.MODE_PRIVATE).edit().remove("sleepAt").apply()
    }
}

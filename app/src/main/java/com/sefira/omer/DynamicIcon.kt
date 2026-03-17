package com.sefira.omer

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import java.util.Calendar

/**
 * Switches the launcher icon to display the current Omer day number.
 * Uses 50 activity-aliases declared in the manifest (one default + 49 day-specific).
 * All aliases point to MainActivity; only one is enabled at a time.
 */
object DynamicIcon {

    private const val PREFS   = "dynamic_icon_prefs"
    private const val KEY_DAY = "last_icon_day"

    /**
     * Call from MainActivity.onResume(), OmerService.onStartCommand().
     * No-ops if the icon is already showing the correct day.
     */
    fun update(context: Context) {
        val today   = Calendar.getInstance()
        val omerDay = AlarmScheduler.omerDayForEvening(today) // -1 outside Omer, 1-49 during
        val displayDay = if (omerDay in 1..49) omerDay else 0 // 0 = default icon

        val prefs   = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastDay = prefs.getInt(KEY_DAY, -1)
        if (lastDay == displayDay) return

        val pkg = context.packageName
        val pm  = context.packageManager

        // The alias we want to show
        val target = ComponentName(pkg,
            if (displayDay in 1..49) "$pkg.LauncherAliasDay$displayDay"
            else "$pkg.LauncherAliasDefault"
        )

        // Enable target first so there's never a moment with no launcher icon
        pm.setComponentEnabledSetting(target,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP)

        // Disable every other alias
        val defaultAlias = ComponentName(pkg, "$pkg.LauncherAliasDefault")
        if (target != defaultAlias) {
            pm.setComponentEnabledSetting(defaultAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP)
        }
        for (day in 1..49) {
            val alias = ComponentName(pkg, "$pkg.LauncherAliasDay$day")
            if (alias != target) {
                pm.setComponentEnabledSetting(alias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP)
            }
        }

        prefs.edit().putInt(KEY_DAY, displayDay).apply()
    }
}

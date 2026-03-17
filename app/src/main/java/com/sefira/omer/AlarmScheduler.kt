package com.sefira.omer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kosherjava.zmanim.ZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.util.Calendar
import java.util.TimeZone

object AlarmScheduler {

    private const val PREFS       = "sefira_prefs"
    private const val KEY_LAT     = "lat"
    private const val KEY_LON     = "lon"
    private const val KEY_ENABLED   = "enabled"
    private const val KEY_TEST_DATE = "test_date_ms"   // -1 = disabled
    private const val KEY_VOLUME    = "volume"         // 0..100
    private const val KEY_VIBRATE   = "vibrate"        // boolean
    private const val KEY_LANGUAGE  = "language"       // "en" | "he" | "both"
    const val REQUEST_CODE          = 1001

    // ── Location ──────────────────────────────────────────────────────────────

    fun saveLocation(context: Context, lat: Double, lon: Double) {
        prefs(context).edit().putFloat(KEY_LAT, lat.toFloat()).putFloat(KEY_LON, lon.toFloat()).apply()
    }

    fun getSavedLocation(context: Context): Pair<Double, Double>? {
        val p = prefs(context)
        if (!p.contains(KEY_LAT)) return null
        return Pair(p.getFloat(KEY_LAT, 0f).toDouble(), p.getFloat(KEY_LON, 0f).toDouble())
    }

    // ── Enable/disable ────────────────────────────────────────────────────────

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, true)

    // ── Volume / Vibration ────────────────────────────────────────────────────

    fun setVolume(context: Context, vol: Int) {
        prefs(context).edit().putInt(KEY_VOLUME, vol.coerceIn(0, 100)).apply()
    }
    fun getVolume(context: Context): Int = prefs(context).getInt(KEY_VOLUME, 80)

    fun setVibrate(context: Context, v: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATE, v).apply()
    }
    fun getVibrate(context: Context): Boolean = prefs(context).getBoolean(KEY_VIBRATE, true)

    // ── Language ("en" | "he" | "both") ──────────────────────────────────────

    fun setLanguage(context: Context, lang: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, lang).apply()
    }
    fun getLanguage(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, "both") ?: "both"

    // ── Test date ─────────────────────────────────────────────────────────────

    /** Store a test date (millis). Pass null to clear. */
    fun setTestDate(context: Context, millis: Long?) {
        prefs(context).edit().putLong(KEY_TEST_DATE, millis ?: -1L).apply()
    }

    /** Returns the stored test date, or null if none. */
    fun getTestDate(context: Context): Calendar? {
        val ms = prefs(context).getLong(KEY_TEST_DATE, -1L)
        if (ms < 0) return null
        return Calendar.getInstance().also { it.timeInMillis = ms }
    }

    fun isTestModeOn(context: Context) = getTestDate(context) != null

    /**
     * Compute what the Omer count would be for the evening of [date].
     * Returns the day number (1–49), or -1 if that night is not in the Omer period.
     */
    fun omerDayForEvening(date: Calendar): Int {
        val next = date.clone() as Calendar
        next.add(Calendar.DATE, 1)
        val jc = JewishCalendar(next)
        val d = jc.dayOfOmer
        return if (d > 0) d else -1
    }

    /**
     * Compute the sunset time for [date] at [lat]/[lon].
     * Returns null if it cannot be computed (polar day/night).
     */
    fun sunsetForDate(lat: Double, lon: Double, date: Calendar): java.util.Date? {
        val geoLoc = GeoLocation("here", lat, lon, 0.0, TimeZone.getDefault())
        val zc = ZmanimCalendar(geoLoc)
        zc.calendar = date
        return zc.sunset
    }

    // ── Scheduling ────────────────────────────────────────────────────────────

    /**
     * Schedule the next Sefira alarm using saved location.
     * Tries tonight first; if sunset already passed, tries tomorrow night.
     */
    fun scheduleNext(context: Context) {
        if (!isEnabled(context)) return
        val (lat, lon) = getSavedLocation(context) ?: return
        val today = Calendar.getInstance()
        if (!scheduleForDate(context, lat, lon, today)) {
            val tomorrow = Calendar.getInstance().also { it.add(Calendar.DATE, 1) }
            scheduleForDate(context, lat, lon, tomorrow)
        }
    }

    /**
     * Try to schedule the alarm for the sunset of [date].
     * Returns true if an alarm was scheduled.
     */
    fun scheduleForDate(context: Context, lat: Double, lon: Double, date: Calendar): Boolean {
        val sunset = sunsetForDate(lat, lon, date) ?: return false

        val omerDay = omerDayForEvening(date)
        if (omerDay < 0) return false

        // Motzei Shabbat: civil Saturday → add 20 min
        var alarmTime = sunset.time
        if (date.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
            alarmTime += 20 * 60 * 1000L
        }

        if (alarmTime <= System.currentTimeMillis()) return false

        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(AlarmReceiver.EXTRA_OMER_DAY, omerDay)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.set(AlarmManager.RTC_WAKEUP, alarmTime, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pi)
        }
        return true
    }

    /**
     * Schedule a test alarm to fire in [delaySeconds] seconds using the
     * Omer count for [testDate]'s evening.
     * Returns the Omer day that was scheduled, or -1 if not an Omer night.
     */
    fun scheduleTestAlarm(context: Context, testDate: Calendar, delaySeconds: Int = 5): Int {
        val omerDay = omerDayForEvening(testDate)
        if (omerDay < 0) return -1

        val fireAt = System.currentTimeMillis() + delaySeconds * 1000L
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(AlarmReceiver.EXTRA_OMER_DAY, omerDay)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE + 99, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.set(AlarmManager.RTC_WAKEUP, fireAt, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        }
        return omerDay
    }

    fun cancelAlarm(context: Context) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(it) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

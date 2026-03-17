package com.sefira.omer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Persistent foreground service that keeps the Sefira alarm alive in the background.
 * Starts when the user enables the reminder; stops itself after Shavuot.
 */
class OmerService : Service() {

    companion object {
        const val CHANNEL_ID = "sefira_service"
        const val NOTIF_ID   = 3001

        fun start(context: Context) {
            val intent = Intent(context, OmerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OmerService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification())

        if (isOmerOver()) {
            AlarmScheduler.cancelAlarm(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        AlarmScheduler.scheduleNext(this)
        return START_STICKY
    }

    /**
     * Returns true once all 49 days have been counted (i.e. we're at Sivan 6 or later).
     */
    private fun isOmerOver(): Boolean {
        // Check if tonight or tomorrow night are still Omer nights
        val today = Calendar.getInstance()
        for (offset in 0..1) {
            val check = today.clone() as Calendar
            if (offset > 0) check.add(Calendar.DATE, offset)
            if (AlarmScheduler.omerDayForEvening(check) > 0) return false
        }
        // No upcoming Omer nights — verify we're actually past Sivan 5 (not before Nisan 16)
        val tomorrow = Calendar.getInstance().also { it.add(Calendar.DATE, 1) }
        val jc = JewishCalendar(tomorrow)
        val month = jc.jewishMonth
        val day   = jc.jewishDayOfMonth
        // NISSAN=1, IYAR=2, SIVAN=3; Omer ends after 5 Sivan
        return (month == JewishCalendar.SIVAN && day >= 6) || month > JewishCalendar.SIVAN
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val tonight = Calendar.getInstance()
        val omerDay = AlarmScheduler.omerDayForEvening(tonight)

        val contentText = when {
            omerDay > 0 -> {
                val (lat, lon) = AlarmScheduler.getSavedLocation(this) ?: Pair(0.0, 0.0)
                val sunset = if (lat != 0.0 || lon != 0.0)
                    AlarmScheduler.sunsetForDate(lat, lon, tonight) else null
                val timeStr = if (sunset != null)
                    " at " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(sunset)
                else ""
                "Tonight: Day $omerDay of the Omer$timeStr"
            }
            else -> {
                val tomorrow = Calendar.getInstance().also { it.add(Calendar.DATE, 1) }
                val nextDay = AlarmScheduler.omerDayForEvening(tomorrow)
                if (nextDay > 0) "Tomorrow: Day $nextDay of the Omer"
                else "Omer reminder is active"
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Sefirat HaOmer")
            .setContentText(contentText)
            .setContentIntent(openPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Sefira Reminder Status",
                        NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shows that the Omer reminder is active"
                    }
                )
            }
        }
    }
}

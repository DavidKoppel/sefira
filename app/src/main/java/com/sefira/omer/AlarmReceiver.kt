package com.sefira.omer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_OMER_DAY = "omer_day"
        const val CHANNEL_ID = "sefira_alarm"
        const val NOTIF_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val omerDay = intent.getIntExtra(EXTRA_OMER_DAY, -1)
        if (omerDay < 1) return

        ensureChannel(context)

        // Launch the full-screen alarm activity
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(EXTRA_OMER_DAY, omerDay)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPi = PendingIntent.getActivity(
            context, 0, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = OmerHelper.getDayLabel(omerDay)
        val text = OmerHelper.getEnglishText(omerDay)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPi, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        nm.notify(NOTIF_ID, notification)

        // Also start the activity directly for older devices / reliable delivery
        context.startActivity(alarmIntent)

        // Switch the launcher icon to today's Omer day immediately
        DynamicIcon.update(context)

        // Restart the background service — it will schedule the next night's alarm
        // and update the persistent notification with the new day info
        OmerService.start(context)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Sefirat HaOmer Alarm",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Nightly reminder to count the Omer"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }
        }
    }
}

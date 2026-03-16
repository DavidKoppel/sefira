package com.sefira.omer

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.sefira.omer.databinding.ActivityAlarmBinding

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private var mediaPlayer: MediaPlayer? = null
    private var toneGen: ToneGenerator? = null
    private val stopHandler = Handler(Looper.getMainLooper())
    private val AUTO_STOP_MS = 10_000L   // 10 seconds
    private val SNOOZE_MS    = 20 * 60 * 1000L  // 20 minutes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn on screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val omerDay = intent.getIntExtra(AlarmReceiver.EXTRA_OMER_DAY, -1)
        if (omerDay < 1) { finish(); return }

        binding.tvDayLabel.text = OmerHelper.getDayLabel(omerDay)
        binding.tvEnglish.text  = OmerHelper.getEnglishText(omerDay)
        binding.tvHebrew.text   = OmerHelper.getHebrewText(omerDay)

        binding.btnStop.setOnClickListener { stopAndDismiss() }
        binding.btnSnooze.setOnClickListener { snooze() }

        startAlarmSound()

        // Auto-stop after 10 s (sound stops; activity stays until user taps)
        stopHandler.postDelayed({ stopAlarmSound() }, AUTO_STOP_MS)
    }

    private fun startAlarmSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            // Fallback: built-in tone
            try {
                toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100).also {
                    it.startTone(ToneGenerator.TONE_PROP_BEEP2, AUTO_STOP_MS.toInt())
                }
            } catch (_: Exception) { }
        }
    }

    private fun stopAlarmSound() {
        stopHandler.removeCallbacksAndMessages(null)
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        toneGen?.release()
        toneGen = null
    }

    private fun snooze() {
        stopAlarmSound()
        dismissNotification()

        // Re-fire alarm in 20 minutes by scheduling a one-off pending intent
        val intent = android.content.Intent(this, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_OMER_DAY, intent.getIntExtra(AlarmReceiver.EXTRA_OMER_DAY, 1))
        }
        val pi = android.app.PendingIntent.getBroadcast(
            this, AlarmScheduler.REQUEST_CODE + 1, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        am.setExactAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + SNOOZE_MS,
            pi
        )
        finish()
    }

    private fun stopAndDismiss() {
        stopAlarmSound()
        dismissNotification()
        finish()
    }

    private fun dismissNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(AlarmReceiver.NOTIF_ID)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
    }
}

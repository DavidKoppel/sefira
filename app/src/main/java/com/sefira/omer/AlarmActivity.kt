package com.sefira.omer

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.sefira.omer.databinding.ActivityAlarmBinding

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private var mediaPlayer: MediaPlayer? = null
    private var toneGen: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private val stopHandler = Handler(Looper.getMainLooper())
    private val AUTO_STOP_MS = 10_000L
    private val SNOOZE_MS    = 20 * 60 * 1000L

    // Vibration: 600 ms on, 300 ms off, repeat
    private val VIBRATE_PATTERN = longArrayOf(0, 600, 300)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        // Apply language preference
        when (AlarmScheduler.getLanguage(this)) {
            "en" -> binding.tvHebrew.visibility  = android.view.View.GONE
            "he" -> binding.tvEnglish.visibility = android.view.View.GONE
            // "both" → show both (default, no change needed)
        }

        binding.btnStop.setOnClickListener { stopAndDismiss() }
        binding.btnSnooze.setOnClickListener { snooze() }

        startAlarmSound()
        startVibration()

        // Auto-stop sound + vibration after 10 seconds; screen stays on for user to tap
        stopHandler.postDelayed({ stopAlarmSound(); stopVibration() }, AUTO_STOP_MS)
    }

    // ── Sound ─────────────────────────────────────────────────────────────────

    private fun startAlarmSound() {
        val volFloat = AlarmScheduler.getVolume(this) / 100f
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
                setVolume(volFloat, volFloat)
                start()
            }
        } catch (_: Exception) {
            try {
                val toneVol = (volFloat * 100).toInt().coerceIn(0, 100)
                toneGen = ToneGenerator(AudioManager.STREAM_ALARM, toneVol).also {
                    it.startTone(ToneGenerator.TONE_PROP_BEEP2, AUTO_STOP_MS.toInt())
                }
            } catch (_: Exception) { }
        }
    }

    private fun stopAlarmSound() {
        stopHandler.removeCallbacksAndMessages(null)
        mediaPlayer?.runCatching { if (isPlaying) stop(); release() }
        mediaPlayer = null
        toneGen?.release()
        toneGen = null
    }

    // ── Vibration ─────────────────────────────────────────────────────────────

    private fun startVibration() {
        if (!AlarmScheduler.getVibrate(this)) return
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(VIBRATE_PATTERN, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(VIBRATE_PATTERN, 0)
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun snooze() {
        stopAlarmSound()
        stopVibration()
        dismissNotification()

        val snoozeIntent = android.content.Intent(this, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_OMER_DAY, intent.getIntExtra(AlarmReceiver.EXTRA_OMER_DAY, 1))
        }
        val pi = android.app.PendingIntent.getBroadcast(
            this, AlarmScheduler.REQUEST_CODE + 1, snoozeIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager)
            .setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + SNOOZE_MS, pi
            )
        finish()
    }

    private fun stopAndDismiss() {
        stopAlarmSound()
        stopVibration()
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
        stopVibration()
    }
}

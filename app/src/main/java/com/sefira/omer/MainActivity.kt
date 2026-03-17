package com.sefira.omer

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.sefira.omer.databinding.ActivityMainBinding
import com.sefira.omer.databinding.DialogSuggestBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val fmt     = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) fetchLocationAndSchedule() else showLocationRationale()
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ── Enable switch ────────────────────────────────────────────────────
        binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            AlarmScheduler.setEnabled(this, isChecked)
            if (isChecked) {
                checkPermissionsAndSchedule()
                OmerService.start(this)
            } else {
                AlarmScheduler.cancelAlarm(this)
                OmerService.stop(this)
                updateStatus()
            }
        }
        binding.btnRefreshLocation.setOnClickListener { fetchLocationAndSchedule() }

        // ── Volume slider ────────────────────────────────────────────────────
        val savedVol = AlarmScheduler.getVolume(this)
        binding.sliderVolume.value = savedVol.toFloat()
        binding.tvVolumePercent.text = "$savedVol%"
        binding.sliderVolume.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            AlarmScheduler.setVolume(this, v)
            binding.tvVolumePercent.text = "$v%"
        }

        // ── Vibrate switch ───────────────────────────────────────────────────
        binding.switchVibrate.isChecked = AlarmScheduler.getVibrate(this)
        binding.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            AlarmScheduler.setVibrate(this, isChecked)
        }

        // ── Language toggle ──────────────────────────────────────────────────
        when (AlarmScheduler.getLanguage(this)) {
            "en"  -> binding.toggleLanguage.check(R.id.btnLangEn)
            "he"  -> binding.toggleLanguage.check(R.id.btnLangHe)
            else  -> binding.toggleLanguage.check(R.id.btnLangBoth)
        }
        binding.toggleLanguage.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                AlarmScheduler.setLanguage(this, when (checkedId) {
                    R.id.btnLangEn -> "en"
                    R.id.btnLangHe -> "he"
                    else           -> "both"
                })
            }
        }

        // ── Suggestions ──────────────────────────────────────────────────────
        binding.btnSuggest.setOnClickListener { showSuggestionDialog() }

        // ── Test mode ────────────────────────────────────────────────────────
        binding.switchTestMode.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                AlarmScheduler.setTestDate(this, null)
                binding.testControls.visibility = View.GONE
            } else {
                binding.testControls.visibility = View.VISIBLE
                if (AlarmScheduler.getTestDate(this) == null) {
                    AlarmScheduler.setTestDate(this, System.currentTimeMillis())
                }
            }
            updateTestPreview()
        }
        binding.btnPickDate.setOnClickListener { showDatePicker() }
        binding.btnFireNow.setOnClickListener  { fireTestAlarmNow() }
    }

    override fun onResume() {
        super.onResume()
        binding.switchEnabled.isChecked = AlarmScheduler.isEnabled(this)
        binding.switchVibrate.isChecked = AlarmScheduler.getVibrate(this)
        val vol = AlarmScheduler.getVolume(this)
        binding.sliderVolume.value = vol.toFloat()
        binding.tvVolumePercent.text = "$vol%"
        // Restore language toggle without triggering the listener
        binding.toggleLanguage.removeOnButtonCheckedListener { _, _, _ -> }
        when (AlarmScheduler.getLanguage(this)) {
            "en" -> binding.toggleLanguage.check(R.id.btnLangEn)
            "he" -> binding.toggleLanguage.check(R.id.btnLangHe)
            else -> binding.toggleLanguage.check(R.id.btnLangBoth)
        }
        val testOn = AlarmScheduler.isTestModeOn(this)
        binding.switchTestMode.isChecked = testOn
        binding.testControls.visibility = if (testOn) View.VISIBLE else View.GONE
        updateStatus()
        updateTestPreview()
        DynamicIcon.update(this)
        if (AlarmScheduler.isEnabled(this)) checkPermissionsAndSchedule()
    }

    // ── Suggestions ───────────────────────────────────────────────────────────

    private fun showSuggestionDialog() {
        val dialogBinding = DialogSuggestBinding.inflate(layoutInflater)

        // Default selection: "New Feature"
        dialogBinding.toggleType.check(R.id.btnTypeFeature)

        AlertDialog.Builder(this)
            .setTitle("Share Your Idea")
            .setView(dialogBinding.root)
            .setPositiveButton("Send Email") { _, _ ->
                val isFeature = dialogBinding.toggleType.checkedButtonId == R.id.btnTypeFeature
                val type = if (isFeature) "New Feature Suggestion" else "New App Idea"
                val body = dialogBinding.etSuggestion.text?.toString()?.trim().orEmpty()
                if (body.isEmpty()) {
                    Toast.makeText(this, "Please describe your idea first", Toast.LENGTH_SHORT).show()
                } else {
                    sendSuggestionEmail(type, body)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendSuggestionEmail(type: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL,   arrayOf("gevaldikApps@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Sefira App — $type")
            putExtra(Intent.EXTRA_TEXT,    body)
        }
        try {
            startActivity(Intent.createChooser(intent, "Send via…"))
        } catch (_: Exception) {
            Toast.makeText(this,
                "No email app found. Please email gevaldikApps@gmail.com",
                Toast.LENGTH_LONG).show()
        }
    }

    // ── Date picker ───────────────────────────────────────────────────────────

    private fun showDatePicker() {
        val current = AlarmScheduler.getTestDate(this)?.timeInMillis
            ?: MaterialDatePicker.todayInUtcMilliseconds()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Choose a test date")
            .setSelection(current)
            .build()
        picker.addOnPositiveButtonClickListener { selUtcMs ->
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).also { it.timeInMillis = selUtcMs }
            val local = Calendar.getInstance().also {
                it.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
                it.set(Calendar.MILLISECOND, 0)
            }
            AlarmScheduler.setTestDate(this, local.timeInMillis)
            updateTestPreview()
        }
        picker.show(supportFragmentManager, "date_picker")
    }

    private fun updateTestPreview() {
        val testCal = AlarmScheduler.getTestDate(this) ?: return
        val isSat   = testCal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
        val omerDay = AlarmScheduler.omerDayForEvening(testCal)
        val (lat, lon) = AlarmScheduler.getSavedLocation(this) ?: Pair(0.0, 0.0)
        val sunset = if (lat != 0.0 || lon != 0.0)
            AlarmScheduler.sunsetForDate(lat, lon, testCal) else null
        val sunsetStr = if (sunset != null) {
            val t = if (isSat) Date(sunset.time + 20 * 60 * 1000L) else sunset
            " (alarm at ${timeFmt.format(t)})"
        } else ""
        val satNote = if (isSat) " — Motzei Shabbat " else " "
        binding.tvTestDate.text = "Test date: ${fmt.format(testCal.time)}$satNote$sunsetStr"
        if (omerDay < 0) {
            binding.tvTestPreview.text = "⚠ Not an Omer night — alarm would not fire"
            binding.tvTestPreview.visibility = View.VISIBLE
            binding.btnFireNow.isEnabled = false
        } else {
            binding.tvTestPreview.text = OmerHelper.getEnglishText(omerDay)
            binding.tvTestPreview.visibility = View.VISIBLE
            binding.btnFireNow.isEnabled = true
        }
    }

    private fun fireTestAlarmNow() {
        val testCal = AlarmScheduler.getTestDate(this) ?: return
        val omerDay = AlarmScheduler.omerDayForEvening(testCal)
        if (omerDay < 0) return
        startActivity(
            Intent(this, AlarmActivity::class.java)
                .putExtra(AlarmReceiver.EXTRA_OMER_DAY, omerDay)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun checkPermissionsAndSchedule() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Exact Alarm Permission")
                    .setMessage("To fire at the exact sunset time, please enable 'Alarms & Reminders' for this app in Settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                    }
                    .setNegativeButton("Skip", null).show()
            }
        }

        val fine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            locationPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            fetchLocationAndSchedule()
        }
    }

    private fun showLocationRationale() {
        AlertDialog.Builder(this)
            .setTitle("Location Required")
            .setMessage("Location is needed to calculate the local sunset time.")
            .setPositiveButton("Grant") { _, _ ->
                locationPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ── Location + Scheduling ─────────────────────────────────────────────────

    private fun fetchLocationAndSchedule() {
        val fine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return

        binding.tvStatus.text = "Getting location…"
        val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        fusedClient.getCurrentLocation(priority, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                val useLoc = loc ?: run {
                    fusedClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            AlarmScheduler.saveLocation(this, last.latitude, last.longitude)
                            OmerService.start(this)
                        }
                        updateStatus(); updateTestPreview()
                    }
                    return@addOnSuccessListener
                }
                AlarmScheduler.saveLocation(this, useLoc.latitude, useLoc.longitude)
                if (AlarmScheduler.isEnabled(this)) OmerService.start(this)
                updateStatus(); updateTestPreview()
            }
            .addOnFailureListener { updateStatus() }
    }

    // ── Status display ────────────────────────────────────────────────────────

    private fun updateStatus() {
        val (lat, lon) = AlarmScheduler.getSavedLocation(this) ?: run {
            binding.tvStatus.text = "No location — tap Refresh"
            binding.tvOmerInfo.visibility = View.GONE
            return
        }

        val tomorrow = Calendar.getInstance().also { it.add(Calendar.DATE, 1) }
        val jc = JewishCalendar(tomorrow)
        if (jc.dayOfOmer <= 0) {
            binding.tvOmerInfo.visibility = View.GONE
            binding.tvStatus.text = "Not currently during Sefirat HaOmer"
            return
        }

        val omerDay = jc.dayOfOmer
        binding.tvOmerInfo.visibility = View.VISIBLE
        binding.tvOmerInfo.text = OmerHelper.getEnglishText(omerDay)

        val today  = Calendar.getInstance()
        val sunset = AlarmScheduler.sunsetForDate(lat, lon, today)
        val isSat  = today.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY

        if (sunset != null) {
            val alarmTime = if (isSat) Date(sunset.time + 20 * 60 * 1000L) else sunset
            binding.tvStatus.text = buildString {
                append("Sunset: ${timeFmt.format(sunset)}")
                if (isSat) append("\nMotzei Shabbat — alarm at ${timeFmt.format(alarmTime)} (+20 min)")
                else append("\nAlarm set for ${timeFmt.format(alarmTime)}")
                append("\n📍 ${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}")
            }
        } else {
            binding.tvStatus.text = "Could not calculate sunset for this location"
        }
    }
}

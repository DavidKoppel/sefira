package com.sefira.omer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restart the background service (which reschedules the alarm) after a device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            if (AlarmScheduler.isEnabled(context)) {
                OmerService.start(context)
            }
        }
    }
}

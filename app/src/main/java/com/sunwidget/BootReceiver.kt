package com.sunwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-schedules the four daily alarms after device reboot.
 * AlarmManager alarms do not survive a power cycle — this receiver restores them.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            SunAlarmReceiver.schedule(context)
        }
    }
}

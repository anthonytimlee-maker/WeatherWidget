package com.sunwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Receives the scheduled alarm and refreshes all widgets.
 * Scheduled at four fixed daily times: 12 AM, 6 AM, 12 PM, 6 PM.
 *
 * Why AlarmManager instead of JobScheduler?
 *   JobScheduler's minimum period is 15 min and it can batch/defer firings.
 *   AlarmManager.setExactAndAllowWhileIdle() fires at the precise wall-clock
 *   time even in Doze mode, which is what we need for a solar-data widget.
 */
class SunAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Refresh all widget instances
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, SunWidgetProvider::class.java))
        ids.forEach { SunWidgetProvider.updateWidget(context, mgr, it) }

        // Schedule the NEXT alarm (keeps the chain going without re-scheduling all four)
        scheduleNext(context)
    }

    companion object {
        private const val REQUEST_CODE = 8472
        const val ACTION_ALARM = "com.sunwidget.ACTION_ALARM"

        /** Hours at which the widget refreshes automatically every day. */
        private val ALARM_HOURS = intArrayOf(0, 6, 12, 18)  // 12AM, 6AM, 12PM, 6PM

        /** Schedule all four daily alarms. Called on first widget add and after reboot. */
        fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            ALARM_HOURS.forEachIndexed { index, hour ->
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextFiringMillis(hour),
                    buildPendingIntent(context, index)
                )
            }
        }

        /** Cancel all four alarms. Called when the last widget is removed. */
        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            ALARM_HOURS.forEachIndexed { index, _ ->
                am.cancel(buildPendingIntent(context, index))
            }
        }

        /**
         * Schedule the single next alarm after a firing.
         * Finds which of the four slots comes next from now and sets it.
         */
        private fun scheduleNext(context: Context) {
            val now = Calendar.getInstance()
            val nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            // Find the next alarm hour after now
            val nextHour = ALARM_HOURS.firstOrNull { it * 60 > nowMins }
                ?: ALARM_HOURS[0]  // wrap to midnight if past 6 PM
            val index = ALARM_HOURS.indexOf(nextHour)

            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextFiringMillis(nextHour),
                buildPendingIntent(context, index)
            )
        }

        /** Returns the epoch milliseconds for the next occurrence of [hour]:00:00 today or tomorrow. */
        private fun nextFiringMillis(hour: Int): Long {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // If that time has already passed today, advance to tomorrow
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        private fun buildPendingIntent(context: Context, requestCode: Int): PendingIntent {
            val intent = Intent(context, SunAlarmReceiver::class.java).apply {
                action = ACTION_ALARM
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE + requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

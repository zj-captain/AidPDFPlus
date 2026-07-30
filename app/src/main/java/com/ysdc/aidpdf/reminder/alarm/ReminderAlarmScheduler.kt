package com.ysdc.aidpdf.reminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ysdc.aidpdf.reminder.config.ReminderConfigRepository
import com.ysdc.aidpdf.store.nextReminderAlarmAt

object ReminderAlarmScheduler {

    private const val REQUEST_CODE = 24_400

    fun scheduleNext(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        val config = ReminderConfigRepository.current
        if (!config.base.enabled || config.base.alarmIntervalMinutes <= 0) {
            cancel(appContext)
            return
        }
        val now = System.currentTimeMillis()
        if (!force && nextReminderAlarmAt > now) return
        if (force) cancel(appContext)
        val triggerAt = now + config.base.alarmIntervalMinutes * 60_000L
        val manager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(appContext))
            nextReminderAlarmAt = triggerAt
        }
    }

    fun markTriggered() {
        nextReminderAlarmAt = 0L
    }

    private fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { manager.cancel(pendingIntent(context)) }
        nextReminderAlarmAt = 0L
    }

    private fun pendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

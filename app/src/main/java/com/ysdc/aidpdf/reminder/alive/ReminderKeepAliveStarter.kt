package com.ysdc.aidpdf.reminder.alive

import android.content.Context
import android.os.SystemClock
import com.ysdc.aidpdf.reminder.alarm.ReminderAlarmScheduler
import com.ysdc.aidpdf.reminder.front.ReminderBarManager
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter

object ReminderKeepAliveStarter {

    private const val WAKE_DEDUPE_MILLIS = 30_000L
    private var lastWakeAt = 0L

    fun wake(context: Context, scheduleJob: Boolean = true) {
        if (!markWakeAllowed()) return
        val appContext = context.applicationContext
        runCatching { ReminderBarManager.startIfAllowed(appContext) }
        runCatching { ReminderTriggerCenter.start(appContext) }
        runCatching { ReminderAlarmScheduler.scheduleNext(appContext) }
        if (scheduleJob) {
            ReminderKeepAliveJobService.schedule(appContext)
        }
    }

    @Synchronized
    private fun markWakeAllowed(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastWakeAt != 0L && now - lastWakeAt < WAKE_DEDUPE_MILLIS) return false
        lastWakeAt = now
        return true
    }
}

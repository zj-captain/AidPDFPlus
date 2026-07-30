package com.ysdc.aidpdf.reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
import com.ysdc.aidpdf.reminder.alive.ReminderKeepAliveJobService

class ReminderRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        ReminderTriggerCenter.start(context.applicationContext)
        ReminderAlarmScheduler.scheduleNext(context, force = true)
        ReminderKeepAliveJobService.schedule(context)
    }
}

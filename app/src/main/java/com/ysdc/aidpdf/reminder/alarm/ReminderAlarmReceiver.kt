package com.ysdc.aidpdf.reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ysdc.aidpdf.reminder.model.ReminderTrigger
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
import com.ysdc.aidpdf.reminder.alive.ReminderKeepAliveService

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        ReminderAlarmScheduler.markTriggered()
        ReminderAlarmScheduler.scheduleNext(context)
        ReminderKeepAliveService.start(context)
        ReminderTriggerCenter.trigger(ReminderTrigger.ALARM) {
            pendingResult.finish()
        }
    }
}

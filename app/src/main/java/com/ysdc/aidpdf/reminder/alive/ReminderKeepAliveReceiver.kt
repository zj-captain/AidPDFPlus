package com.ysdc.aidpdf.reminder.alive

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderKeepAliveReceiver : BroadcastReceiver() {

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context?, intent: Intent?) {
        val safeContext = context ?: return
        ReminderKeepAliveStarter.wake(safeContext)
    }
}

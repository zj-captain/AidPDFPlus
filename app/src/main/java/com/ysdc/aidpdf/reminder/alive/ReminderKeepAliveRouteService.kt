package com.ysdc.aidpdf.reminder.alive

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ReminderKeepAliveRouteService : Service() {

    override fun onCreate() {
        super.onCreate()
        ReminderKeepAliveStarter.wake(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ReminderKeepAliveStarter.wake(this)
        return START_STICKY
    }
}

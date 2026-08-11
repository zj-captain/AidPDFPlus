package com.ysdc.aidpdf.reminder.front

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class ReminderBarService : Service() {

    companion object {
        var isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        showForegroundBar()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        showForegroundBar()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showForegroundBar() {
//        if (!ReminderBarManager.isAllowed(this)) {
//            stopSelf()
//            return
//        }
        try {
            startForeground(
                ReminderBarManager.NOTIFICATION_ID,
                ReminderBarManager.showNotification(this)
            )
        } catch (first: Exception) {
            try {
                startForeground(
                    ReminderBarManager.NOTIFICATION_ID,
                    ReminderBarManager.showNotification(this)
                )
            } catch (second: Exception) {
                Log.e("ReminderBarService", "Failed to start foreground notification", second)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        super.onDestroy()
    }
}

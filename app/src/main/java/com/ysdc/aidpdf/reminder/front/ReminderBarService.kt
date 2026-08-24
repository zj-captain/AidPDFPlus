package com.ysdc.aidpdf.reminder.front

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.ysdc.aidpdf.core.permission.canPostNotifications
import com.ysdc.aidpdf.reminder.front.ReminderBarManager.NOTIFICATION_ID

class ReminderBarService : Service() {

    companion object {
        var isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        if (!canPostNotifications()) {
            stopSelf()
            return
        }
        isServiceRunning = true
        startForegroundInternal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        updateNotice()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotice() {
        runCatching {
//            val isShowing = isNotificationShowing(NOTIFICATION_ID)
//            if (!isShowing) {
//                ReportUtils.reportEvent(InsertEvent.ALWAYS_NOTIFICATION_TRIGGER)
//            }
            // 再次调用 startForeground 以刷新通知
            startForegroundInternal()
        }.onFailure {
//            Log.e("AdoCleanService", "updateNotice failed", it)
            // 服务在 onCreate 已尝试进入前台，这里只更新通知，失败不再 stopSelf
        }
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundInternal() {
        try {
            val notification = ReminderBarManager.showNotification(this)
            startForegroundWithNotification(notification)
//            Log.e("AdoCleanService", "startForegroundInternal: 111")
        } catch (e: Throwable) {
            try {
                val fallbackNotification = ReminderBarManager.createStandardPersistentNotification(this.applicationContext)
                startForegroundWithNotification(fallbackNotification)
            } catch (_: Throwable) {
                stopSelf()
            }
        }
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundWithNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        super.onDestroy()
    }
}

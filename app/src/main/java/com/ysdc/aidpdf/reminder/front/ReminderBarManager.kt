package com.ysdc.aidpdf.reminder.front

import android.annotation.SuppressLint
import android.app.Application
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.core.permission.canPostNotifications
import com.ysdc.aidpdf.reminder.ReminderDeviceCompat
import com.ysdc.aidpdf.reminder.ReminderDeviceCompat.isAndroid16AndAbove
import com.ysdc.aidpdf.reminder.ReminderDeviceCompat.isOneNoticeDevice
import com.ysdc.aidpdf.reminder.ReminderEligibilityPolicy
import com.ysdc.aidpdf.reminder.ReminderIntents
import com.ysdc.aidpdf.reminder.ReminderEventTracker
import com.ysdc.aidpdf.reminder.config.ReminderConfigRepository
import com.ysdc.aidpdf.reminder.model.ReminderTarget
import com.ysdc.aidpdf.reminder.model.ReminderSource
import kotlin.random.Random

object ReminderBarManager {

    private const val TAG = "ReminderBarManager"
    const val NOTIFICATION_ID = 24_300
    private const val CHANNEL_ID = "aidpdf_document_bar"

    fun startIfAllowed(context: Context) {
        if (!context.canPostNotifications()) {
            Log.e(TAG, "startIfAllowed: 无通知权限，直接停止")
            stop(context)
            return
        }
        if (ReminderDeviceCompat.isAndroid12AndAbove() && context is Application ||!ReminderDeviceCompat.isAndroid12AndAbove()) {
            Log.e(TAG, "startIfAllowed: Android12+ 且 Application，仅 showNotification 不启动服务")
            runCatching { showNotification(context) }
            return
        }
        runCatching {
            if (ReminderBarService.isServiceRunning) {
                Log.e(TAG, "startIfAllowed: 服务已在运行，检查通知是否被移除")
                val removed = NotificationManagerCompat.from(context)
                    .activeNotifications
                    .none { it.id == NOTIFICATION_ID }
                if (removed) showNotification(context)
                return
            } else {
                Log.e(TAG, "startIfAllowed: 调用 startForegroundService 启动服务")
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ReminderBarService::class.java)
                )
            }
        }.onFailure {
            // 关键日志：startForegroundService 抛异常时，此前会被 runCatching 静默吞掉
            Log.e(TAG, "startIfAllowed: startForegroundService 抛出异常", it)
        }
    }

    fun isAllowed(context: Context): Boolean {
        return ReminderConfigRepository.current.base.enabled &&
                ReminderEligibilityPolicy.canSend(context) &&
                context.canPostNotifications()
    }

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, ReminderBarService::class.java)) }
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    fun showNotification(context: Context): Notification {
        ensureChannel(context)
        val normal = remoteView(context, R.layout.layout_reminder_bar)
        val compact = remoteView(context, R.layout.layout_reminder_bar_compact)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.reminder_bar_channel_name))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(
                if (ReminderDeviceCompat.isOneNoticeDevice() && ReminderDeviceCompat.isAndroid16AndAbove()) {
                    NotificationCompat.PRIORITY_MIN
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup("AidPDF${Random.nextInt()}")
            .setGroupSummary(false)
            .setCustomContentView(compact)
            .setCustomBigContentView(normal)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(
                ReminderIntents.pendingOpenIntent(
                    context,
                    NOTIFICATION_ID,
                    ReminderTarget.PDF,
                    source = ReminderSource.ALWAYS
                )
            )
            .build()
            .also {
//                it.flags = it.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
            }
        runCatching {
            val isShowing = context.isNotificationShowing(NOTIFICATION_ID)
            if (!isShowing) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
                ReminderEventTracker.reportAlwaysTriggered()
            }

        }
        return notification
    }

    /**
     * 使用标准通知布局创建常驻通知（无自定义 RemoteViews），
     * 用于自定义 RemoteViews 在部分机型 / Android 15 上 inflate 失败时的兜底。
     */
    fun createStandardPersistentNotification(context: Context): Notification {
        // 确保通知渠道已创建
        NotificationManagerCompat.from(context)
            .createNotificationChannel(
                NotificationChannelCompat.Builder(
                    CHANNEL_ID,
                    if (isOneNoticeDevice() && isAndroid16AndAbove()) {
                        NotificationManagerCompat.IMPORTANCE_MIN
                    } else {
                        NotificationManagerCompat.IMPORTANCE_DEFAULT
                    }
                )
                    .setSound(null, null)
                    .setLightsEnabled(false)
                    .setVibrationEnabled(false)
                    .setShowBadge(false)
                    .setName(CHANNEL_ID)
                    .build()
            )

        return NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_notification_small)
            setContentTitle("AdoPDF")
            setContentText(context.getString(R.string.reminder_bar_channel_name))
            setPriority(
                if (isOneNoticeDevice() && isAndroid16AndAbove()) {
                    NotificationCompat.PRIORITY_MIN
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            setCategory(NotificationCompat.CATEGORY_SERVICE)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setSound(null)
            setVibrate(null)
            setGroupSummary(false)
            setGroup("")
        }.build()
    }

    /**
     * 最简兜底通知：仅用于保证 startForeground() 一定能完成，避免
     * startForegroundService() 后未调用 startForeground() 导致的 RemoteServiceException。
     */
    fun createMinimalNotification(context: Context): Notification {
        NotificationManagerCompat.from(context)
            .createNotificationChannel(
                NotificationChannelCompat.Builder(
                    CHANNEL_ID,
                    if (isOneNoticeDevice() && isAndroid16AndAbove()) {
                        NotificationManagerCompat.IMPORTANCE_MIN
                    } else {
                        NotificationManagerCompat.IMPORTANCE_DEFAULT
                    }
                )
                    .setName(CHANNEL_ID)
                    .build()
            )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle("AdoPDF")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    private fun remoteView(context: Context, layoutId: Int): RemoteViews {
        return RemoteViews(context.packageName, layoutId).apply {
            setOnClickPendingIntent(
                R.id.reminder_bar_pdf,
                ReminderIntents.pendingOpenIntent(
                    context,
                    NOTIFICATION_ID + 1,
                    ReminderTarget.PDF,
                    source = ReminderSource.ALWAYS
                )
            )
            setOnClickPendingIntent(
                R.id.reminder_bar_excel,
                ReminderIntents.pendingOpenIntent(
                    context,
                    NOTIFICATION_ID + 2,
                    ReminderTarget.EXCEL,
                    source = ReminderSource.ALWAYS
                )
            )
            setOnClickPendingIntent(
                R.id.reminder_bar_ppt,
                ReminderIntents.pendingOpenIntent(
                    context,
                    NOTIFICATION_ID + 3,
                    ReminderTarget.PPT,
                    source = ReminderSource.ALWAYS
                )
            )
        }
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            if (ReminderDeviceCompat.isOneNoticeDevice() && ReminderDeviceCompat.isAndroid16AndAbove()) {
                NotificationManagerCompat.IMPORTANCE_MIN
            } else {
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            }
        )
            .setName(context.getString(R.string.reminder_bar_channel_name))
            .setSound(null, null)
            .setLightsEnabled(false)
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }
}

//检查指定id的通知是否显示
fun Context.isNotificationShowing(notifyId: Int): Boolean {
    return NotificationManagerCompat.from(this)
        .activeNotifications
        .any { it.id == notifyId }
}

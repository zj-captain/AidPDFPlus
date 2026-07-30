package com.ysdc.aidpdf.reminder.front

import android.annotation.SuppressLint
import android.app.Application
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.core.permission.canPostNotifications
import com.ysdc.aidpdf.reminder.ReminderDeviceCompat
import com.ysdc.aidpdf.reminder.ReminderEligibilityPolicy
import com.ysdc.aidpdf.reminder.ReminderIntents
import com.ysdc.aidpdf.reminder.ReminderEventTracker
import com.ysdc.aidpdf.reminder.config.ReminderConfigRepository
import com.ysdc.aidpdf.reminder.model.ReminderTarget
import com.ysdc.aidpdf.reminder.model.ReminderSource
import kotlin.random.Random

object ReminderBarManager {

    const val NOTIFICATION_ID = 24_300
    private const val CHANNEL_ID = "aidpdf_document_bar"

    fun startIfAllowed(context: Context) {
        if (!isAllowed(context)) {
            stop(context)
            return
        }
        if (ReminderDeviceCompat.isAndroid12AndAbove() && context is Application) {
            showNotification(context)
            return
        }
        runCatching {
            if (ReminderBarService.isServiceRunning) {
                val removed = NotificationManagerCompat.from(context)
                    .activeNotifications
                    .none { it.id == NOTIFICATION_ID }
                if (removed) showNotification(context)
                return
            }
            ContextCompat.startForegroundService(
                context,
                Intent(context, ReminderBarService::class.java)
            )
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
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            ReminderEventTracker.reportAlwaysTriggered()
        }
        return notification
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

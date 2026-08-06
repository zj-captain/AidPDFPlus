package com.ysdc.aidpdf.reminder.notice

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.core.block.DeviceSignals
import com.ysdc.aidpdf.core.permission.canPostNotifications
import com.ysdc.aidpdf.reminder.ReminderDeviceCompat
import com.ysdc.aidpdf.reminder.ReminderEligibilityPolicy
import com.ysdc.aidpdf.reminder.ReminderIntents
import com.ysdc.aidpdf.reminder.ReminderEventTracker
import com.ysdc.aidpdf.reminder.model.EXTRA_REMINDER_NOTICE_ID
import com.ysdc.aidpdf.reminder.model.ReminderMessage
import com.ysdc.aidpdf.reminder.model.ReminderTrigger
import com.ysdc.aidpdf.reminder.model.ReminderSource
import com.ysdc.aidpdf.reminder.overlay.ReminderOverlayController
import com.ysdc.aidpdf.reminder.store.ReminderStatsStore
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
import com.ysdc.aidpdf.store.appInstance
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

object ReminderNotificationCenter {

    private const val REMINDER_CHANNEL_ID = "aidpdf_reminders"
    private const val REFRESH_CHANNEL_ID = "aidpdf_reminders_refresh"
    private const val MEDIA_CHANNEL_ID = "aidpdf_media_reminders"
    private const val MEDIA_NOTICE_ID = 24_200
    private const val REFRESH_TOTAL_TIMES = 30
    private const val REFRESH_INTERVAL_SECONDS = 2L
    private const val WAKE_LOCK_TIMEOUT_MILLIS = 3_100L

    private val refreshScope = CoroutineScope(
        Dispatchers.Main.immediate + SupervisorJob() + CoroutineExceptionHandler { _, _ -> }
    )
    private var refreshJob: Job? = null
    private var mediaSession: MediaSessionCompat? = null

    lateinit var popRefresh: PopRefresh //通知刷新配置

    val notificationManager: NotificationManager by lazy {
        appInstance.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun showSystem(context: Context, message: ReminderMessage): Boolean {
        val appContext = context.applicationContext
        if (!ReminderEligibilityPolicy.canSend(appContext)) return false
        if (!appContext.canPostNotifications()) return false
        wakeScreenIfNeeded(appContext, message.trigger)
        ensureReminderChannel(appContext)
        val slot = ReminderStatsStore.nextSystemNoticeSlot(message.trigger)
        val id = ReminderSystemNoticeIds.idFor(message.trigger, slot)
        val published = publish(
            appContext,
            id,
            buildSystemNotification(appContext, REMINDER_CHANNEL_ID, id, message)
        )
        if (published) {
            ReminderEventTracker.reportChannelSent(message.trigger, ReminderSource.SYSTEM)
            scheduleSystemRefresh(appContext, id, message)
        }
        return published
    }

    @Suppress("DEPRECATION")
    fun showMedia(context: Context, message: ReminderMessage): Boolean {
        val appContext = context.applicationContext
        if (!ReminderEligibilityPolicy.canSend(appContext)) return false
        wakeScreenIfNeeded(appContext, message.trigger)
        ensureMediaChannel(appContext)
        val clickIntent = ReminderIntents.pendingOpenIntent(
            context = appContext,
            requestCode = MEDIA_NOTICE_ID,
            target = message.content.target,
            trigger = message.trigger,
            noticeId = MEDIA_NOTICE_ID,
            source = ReminderSource.MEDIA
        )
        mediaSession?.release()
        val session = MediaSessionCompat(appContext, "AidPdfReminderSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }
        mediaSession = session
        val notification = NotificationCompat.Builder(appContext, MEDIA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setAutoCancel(true)
            .setOngoing(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentText(message.content.button)
            .setContentTitle(message.content.text)
            .setContentIntent(clickIntent)
            .setStyle(MediaStyle().setMediaSession(session.sessionToken))
            .build()
        val published = publish(appContext, MEDIA_NOTICE_ID, notification)
        if (!published) {
            session.release()
            if (mediaSession === session) mediaSession = null
        }
        if (published) {
            ReminderEventTracker.reportChannelSent(message.trigger, ReminderSource.MEDIA)
        }
        return published
    }

    fun stopSystemRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    fun cancelSystemNotices(context: Context) {
        stopSystemRefresh()
        val manager = NotificationManagerCompat.from(context)
        ReminderSystemNoticeIds.allIds().forEach(manager::cancel)
    }

    fun cancelMediaNotice(context: Context) {
        NotificationManagerCompat.from(context).cancel(MEDIA_NOTICE_ID)
        mediaSession?.release()
        mediaSession = null
    }

    fun cancelReminderNotices(context: Context) {
        cancelSystemNotices(context)
        cancelMediaNotice(context)
    }

    fun cancelClicked(context: Context, intent: Intent? = null) {
        stopSystemRefresh()
        intent?.getIntExtra(EXTRA_REMINDER_NOTICE_ID, 0)
            ?.takeIf { it in ReminderSystemNoticeIds.allIds() }
            ?.let { NotificationManagerCompat.from(context).cancel(it) }
        cancelMediaNotice(context)
    }

    private fun buildSystemNotification(
        context: Context,
        channelId: String,
        id: Int,
        message: ReminderMessage,
    ): Notification {
        val clickIntent = ReminderIntents.pendingOpenIntent(
            context = context,
            requestCode = id,
            target = message.content.target,
            trigger = message.trigger,
            noticeId = id,
            source = ReminderSource.SYSTEM
        )
        val expanded =
            remoteView(context, R.layout.layout_reminder_notice_expanded, message, clickIntent)
        val compact =
            remoteView(context, R.layout.layout_reminder_notice_compact, message, clickIntent)
        val tiny = remoteView(context, R.layout.layout_reminder_notice_tiny, message, clickIntent)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup("AidPDF_notice_${Random.nextInt()}")
            .setGroupSummary(false)
            .setContentTitle(message.content.button)
            .setContentText(message.content.text)
            .setContentIntent(clickIntent)

        configureCustomViews(builder, expanded, compact, tiny)
        if (channelId == REFRESH_CHANNEL_ID) {
            builder.setDefaults(0).setSound(null).setVibrate(null).setOnlyAlertOnce(true)
        }
        return builder.build().also { notification ->
//            notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
        }
    }

    private fun buildRefreshSystemNotification(
        context: Context,
        channelId: String,
        id: Int,
        message: ReminderMessage,
    ): Notification {
        Log.e("TAG", "buildRefreshSystemNotification: $channelId")
        val clickIntent = ReminderIntents.pendingOpenIntent(
            context = context,
            requestCode = id,
            target = message.content.target,
            trigger = message.trigger,
            noticeId = id,
            source = ReminderSource.SYSTEM
        )
        val expanded =
            remoteView(context, R.layout.layout_reminder_notice_expanded, message, clickIntent)
        val compact =
            remoteView(context, R.layout.layout_reminder_notice_compact, message, clickIntent)
        val tiny = remoteView(context, R.layout.layout_reminder_notice_tiny, message, clickIntent)
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(
                channelId,
                NotificationManagerCompat.IMPORTANCE_MAX
            )
                .apply {
                    setSound(null, null)
                    setVibrationEnabled(false)
                    setLightsEnabled(false)
                }
                .setShowBadge(true)
                .setName(channelId)
                .build()
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .apply {
                setSmallIcon(R.drawable.ic_notification_small)
                setGroupSummary(false)
                setGroup("")
                setAutoCancel(true)
                setContentIntent(clickIntent)
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                setPriority(NotificationCompat.PRIORITY_MAX)
                setOngoing(false)
            }
        configureCustomViews(builder, expanded, compact, tiny)
        return builder.build()
    }

    private fun configureCustomViews(
        builder: NotificationCompat.Builder,
        expanded: RemoteViews,
        compact: RemoteViews,
        tiny: RemoteViews,
    ) {
        if (ReminderDeviceCompat.isAndroid12AndAbove()) {
            if (ReminderDeviceCompat.isXiaomi()) {
                builder.setCustomContentView(compact)
            } else {
                builder.setCustomContentView(tiny).setCustomHeadsUpContentView(compact)
            }
            builder
                .setCustomBigContentView(expanded)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        } else if (
            ReminderDeviceCompat.isSamsung() ||
            ReminderDeviceCompat.isXiaomi() ||
            ReminderDeviceCompat.isOnePlus()
        ) {
            builder
                .setCustomContentView(compact)
                .setCustomHeadsUpContentView(compact)
                .setCustomBigContentView(expanded)
        } else {
            builder
                .setCustomContentView(expanded)
                .setCustomHeadsUpContentView(expanded)
                .setCustomBigContentView(expanded)
        }
    }

    private fun remoteView(
        context: Context,
        layoutId: Int,
        message: ReminderMessage,
        clickIntent: PendingIntent,
    ): RemoteViews {
        return RemoteViews(context.packageName, layoutId).apply {
            setTextViewText(R.id.reminder_message, message.content.text)
            setTextViewText(R.id.reminder_action, message.content.button)
            if (layoutId == R.layout.layout_reminder_notice_expanded) {
                setImageViewResource(R.id.reminder_image, message.content.imageRes)
            }
            setOnClickPendingIntent(R.id.reminder_root, clickIntent)
            setOnClickPendingIntent(R.id.reminder_action, clickIntent)
        }
    }

    private fun scheduleSystemRefresh(context: Context, id: Int, message: ReminderMessage) {
        stopSystemRefresh()
        if (!canStartSystemRefresh(context)) return
        refreshJob = refreshScope.launch {
            val refreshNotification =
                buildRefreshSystemNotification(context, REFRESH_CHANNEL_ID, id, message)
            repeat(popRefresh.times - 1) {
                delay(popRefresh.interval * 1_000L)
                if (!canContinueSystemRefresh(context)) return@launch
//                ensureRefreshChannel(context)
                Log.e("TAG", "scheduleSystemRefresh: id = $id")
                withContext(Dispatchers.Main) {
                    notificationManager.notify(id, refreshNotification)
                }
            }
        }
    }

    private fun canStartSystemRefresh(context: Context): Boolean {
        return BlockUtils.shouldBlockAds(context).not() && !DeviceSignals.isSamsung() &&
                context.canPostNotifications() &&
                !ReminderTriggerCenter.isAppInForeground() &&
                !ReminderOverlayController.isShowing() &&
                popRefresh.popRefreshSwitch == 1
    }

    private fun canContinueSystemRefresh(context: Context): Boolean {
        return canStartSystemRefresh(context) && ReminderDeviceCompat.isScreenInteractive(context)
    }

    private fun wakeScreenIfNeeded(context: Context, trigger: ReminderTrigger) {
        if (trigger == ReminderTrigger.UNLOCK || ReminderDeviceCompat.isScreenInteractive(context)) return
        runCatching {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "${context.packageName}:notify"
            )
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        }
    }

    private fun ensureReminderChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(
            REMINDER_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_MAX
        )
            .setName(context.getString(R.string.reminder_channel_name))
            .setLightsEnabled(true)
            .setVibrationEnabled(true)
            .setShowBadge(true)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun ensureRefreshChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(
            REFRESH_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_MAX
        )
            .setName(context.getString(R.string.reminder_channel_name))
            .setLightsEnabled(false)
            .setVibrationEnabled(false)
            .setSound(null, null)
            .setShowBadge(true)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun ensureMediaChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(
            MEDIA_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_MAX
        )
            .setName(context.getString(R.string.reminder_media_channel_name))
            .setLightsEnabled(true)
            .setVibrationEnabled(true)
            .setShowBadge(true)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun publish(context: Context, id: Int, notification: Notification): Boolean {
        return runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        }.getOrDefault(false)
    }
}

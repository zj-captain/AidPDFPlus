package com.ysdc.aidpdf.reminder.task

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ysdc.aidpdf.ad.AdsAdmobLimitManager
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.core.permission.canDrawOverlays
import com.ysdc.aidpdf.core.permission.canPostNotifications
import com.ysdc.aidpdf.reminder.ReminderEligibilityPolicy
import com.ysdc.aidpdf.reminder.ReminderEventTracker
import com.ysdc.aidpdf.reminder.alarm.ReminderAlarmScheduler
import com.ysdc.aidpdf.reminder.alive.ReminderKeepAliveService
import com.ysdc.aidpdf.reminder.config.Media2Manager
import com.ysdc.aidpdf.reminder.config.ReminderConfigRepository
import com.ysdc.aidpdf.reminder.config.ReminderQuietHours
import com.ysdc.aidpdf.reminder.content.ReminderContentPool
import com.ysdc.aidpdf.reminder.front.ReminderBarManager
import com.ysdc.aidpdf.reminder.model.ReminderMessage
import com.ysdc.aidpdf.reminder.model.ReminderTrigger
import com.ysdc.aidpdf.reminder.notice.ReminderNotificationCenter
import com.ysdc.aidpdf.reminder.overlay.ReminderOverlayController
import com.ysdc.aidpdf.reminder.overlay.ReminderOverlayPolicy
import com.ysdc.aidpdf.reminder.store.ReminderStatsStore
import com.ysdc.aidpdf.reminder.utils.ManufacturerUtils
import com.ysdc.aidpdf.store.appInstance
import com.ysdc.aidpdf.store.mediaNoticeLastShowTime
import com.ysdc.aidpdf.tracking.AidEventHub
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import com.ysdc.aidpdf.tracking.TrackingEventNames
import org.bouncycastle.oer.its.ieee1609dot2.EndEntityType.app

object ReminderTriggerCenter {

    private const val ACTION_CLOSE_SYSTEM_DIALOGS = "android.intent.action.CLOSE_SYSTEM_DIALOGS"
    private const val EXTRA_SYSTEM_DIALOG_REASON = "reason"
    private const val REASON_HOME = "homekey"
    private const val REASON_HOME_GESTURE = "fs_gesture"
    private const val MINUTE_MILLIS = 60_000L
    private const val TIMER_FIRST_DELAY_MILLIS = 5_000L
    private const val SYSTEM_DIALOG_DEDUP_MILLIS = 1_000L

    private val scope =
        CoroutineScope(Dispatchers.Main + SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
    private val triggerMutex = Mutex()

    @Volatile
    private var started = false

    @Volatile
    private var foregroundCount = 0
    private var application: Application? = null
    private var enteredForeground = false
    private var timerJob: Job? = null
    private var exitJob: Job? = null
    private var homeJob: Job? = null
    private var recentJob: Job? = null
    private var adClickJob: Job? = null
    private var unlockReceiver: BroadcastReceiver? = null
    private var systemDialogReceiver: BroadcastReceiver? = null
    @Volatile
    private var lastSystemDialogTriggerAt = 0L

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext as? Application ?: return
        application = app
        if (started) return
        started = true
        startMinuteChecks()
        ReminderAlarmScheduler.scheduleNext(app)
    }

    fun onConfigUpdated() {
        val app = application ?: return
        ReminderAlarmScheduler.scheduleNext(app, force = true)
        if (!ReminderConfigRepository.current.base.enabled || !ReminderEligibilityPolicy.canSend(app)) {
            ReminderBarManager.stop(app)
            ReminderNotificationCenter.cancelReminderNotices(app)
            ReminderOverlayController.hide()
        } else if (foregroundCount > 0) {
            ReminderBarManager.startIfAllowed(app)
        }
    }

    fun onForegroundCountChanged(count: Int, triggerExit: Boolean = true) {
        foregroundCount = count.coerceAtLeast(0)
        if (foregroundCount > 0) {
            enteredForeground = true
            exitJob?.cancel()
            ReminderOverlayController.hide()
            application?.let(ReminderBarManager::startIfAllowed)
            return
        }
        if (!triggerExit || !enteredForeground) {
            exitJob?.cancel()
            return
        }
        scheduleDelayed(ReminderTrigger.APP_EXIT)
    }

    fun onUserLeaveHint() {
        scheduleDelayed(ReminderTrigger.HOME)
    }

    fun onAdClicked() {
        scheduleDelayed(ReminderTrigger.AD_CLICK)
    }

    fun trigger(trigger: ReminderTrigger, onComplete: () -> Unit = {}) {
        Log.e("TAG", "trigger: $trigger")
        scope.launch {
            try {
                triggerMutex.withLock {
                    performTrigger(trigger)
                }
            } finally {
                runCatching(onComplete)
            }
        }
    }

    fun isAppInForeground(): Boolean = foregroundCount > 0

    private suspend fun performTrigger(trigger: ReminderTrigger) {
        val app = application ?: return
        //新增非三星手机发送媒体通知业务逻辑，和原有逻辑并行---------开始(新媒体2)
        if (!ManufacturerUtils.isSamsungDevice() && Media2Manager.config.switch == 1){
            AidAdHub.log("媒体2逻辑开始执行")
            if (!isAppInForeground()){
                AidAdHub.log("媒体2逻辑开始执行---")
                if (System.currentTimeMillis() - mediaNoticeLastShowTime > Media2Manager.config.intervalTime * 60_000L || mediaNoticeLastShowTime == 0L){
                    val msg = ReminderContentPool.next(app, trigger)
                    val result = ReminderNotificationCenter.showMedia2(app,msg )
                    mediaNoticeLastShowTime = System.currentTimeMillis()
                    if (result) {
                        AidEventHub.track("media2_notification_trigger")
                    }
                    AidAdHub.log("媒体2逻辑开始执行结束")
                }
            }
        }
        //---------------结束
        if (!canShow(app, trigger)) return
        val message = ReminderContentPool.next(app, trigger)
        val overlayShown = tryShowOverlay(app, message)
        val systemShown = if (overlayShown) {
            false
        } else if (app.canPostNotifications()) {
            ReminderNotificationCenter.showSystem(app, message)
        } else {
            ReminderNotificationCenter.cancelSystemNotices(app)
            false
        }
        val mediaShown = ReminderNotificationCenter.showMedia(app, message)
        if (overlayShown || systemShown || mediaShown) {
            ReminderStatsStore.record(trigger)
            ReminderEventTracker.reportTriggerSuccess()
        }
    }

    private fun tryShowOverlay(app: Application, message: ReminderMessage): Boolean {
        if (AdsAdmobLimitManager.canShow()) return false
        if (ReminderOverlayController.isShowing()) return false
        if (!app.canDrawOverlays()) return false
        if (!ReminderOverlayPolicy.firstIntervalPassed(app)) return false
        ReminderNotificationCenter.cancelSystemNotices(app)
        return ReminderOverlayController.show(app, message)
    }

    private fun canShow(context: Context, trigger: ReminderTrigger): Boolean {
        val config = ReminderConfigRepository.current
        if (!ReminderEligibilityPolicy.canSend(context)) return false
//        ReminderEventTracker.reportDetectionPass(TrackingEventNames.SHIELD_DETECTION_PASS)
        if (
            ReminderQuietHours.contains(
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                config.base.quietStartHour,
                config.base.quietEndHour
            )
        ) return false
//        ReminderEventTracker.reportDetectionPass(TrackingEventNames.PERIOD_DETECTION_PASS)
        if (!config.isEnabled(trigger)) return false
//        ReminderEventTracker.reportDetectionPass(TrackingEventNames.SWITCH_DETECTION_PASS)
        if (isAppInForeground()) return false
//        ReminderEventTracker.reportDetectionPass(TrackingEventNames.BACK_DETECTION_PASS)
        if (trigger.requiresInteractiveScreen && !screenIsInteractive(context)) return false
        val stats = ReminderStatsStore.read(trigger)
        val interval = config.intervalMinutes(trigger)
        if (interval > 0 && stats.lastShownAt > 0L) {
            val elapsed = System.currentTimeMillis() - stats.lastShownAt
            if (elapsed < interval * MINUTE_MILLIS) return false
        }
//        ReminderEventTracker.reportDetectionPass(TrackingEventNames.INTERVAL_DETECTION_PASS)
        val limit = config.dailyLimit(trigger)
        if (limit <= 0 || stats.count >= limit) return false
//        ReminderEventTracker.reportDetectionPass(TrackingEventNames.DAILY_DETECTION_PASS)
        return true
    }

    private fun screenIsInteractive(context: Context): Boolean {
        return runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        }.getOrDefault(false)
    }

    private fun startMinuteChecks() {
        timerJob?.cancel()
        timerJob = scope.launch {
            delay(TIMER_FIRST_DELAY_MILLIS)
            while (true) {
                trigger(ReminderTrigger.TIME)
                delay(MINUTE_MILLIS)
            }
        }
    }

     fun registerReceivers(context: Context) {
        unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                Log.e("unlockReceiver", "scheduleDelayed: unlockReceiver")
//                if (intent?.action != Intent.ACTION_USER_PRESENT) return
                scope.launch {
                    delay(800L)
                    trigger(ReminderTrigger.UNLOCK)
                    runCatching { ReminderKeepAliveService.start(context) }
                }
            }
        }.also { receiver ->
            appInstance.registerReceiver(
                receiver,
                IntentFilter().also {
                    it.addAction(Intent.ACTION_USER_PRESENT)
                    it.addAction(Intent.ACTION_USER_UNLOCKED)
                }
            )
        }

        systemDialogReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                Log.e("unlockReceiver", "scheduleDelayed: unlockReceiver----------")
                if (intent?.action != ACTION_CLOSE_SYSTEM_DIALOGS) return
                val now = System.currentTimeMillis()
                if (now - lastSystemDialogTriggerAt < SYSTEM_DIALOG_DEDUP_MILLIS) {
                    // 1 秒内系统对话框关闭广播可能重复触发，这里统一去重避免重复调度。
                    Log.d("unlockReceiver", "系统对话框广播 1s 内重复触发，已忽略")
                    return
                }
                lastSystemDialogTriggerAt = now
                when (intent.getStringExtra(EXTRA_SYSTEM_DIALOG_REASON)) {
                    REASON_HOME, REASON_HOME_GESTURE -> scheduleDelayed(ReminderTrigger.HOME)
                    "recentapps" -> scheduleDelayed(ReminderTrigger.RECENT)
                }
            }
        }.also { receiver ->
            ContextCompat.registerReceiver(appInstance, receiver, IntentFilter().also {
                it.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    private fun scheduleDelayed(trigger: ReminderTrigger) {
        Log.e("TAG", "scheduleDelayed: $trigger", )
        if (!trigger.isAdditionalScene) return
        if (trigger == ReminderTrigger.RECENT) homeJob?.cancel()
        val job = scope.launch {
            val seconds = ReminderConfigRepository.current.delaySeconds(trigger)
            delay(seconds.coerceAtLeast(0) * 1000L)
            trigger(trigger)
        }
        when (trigger) {
            ReminderTrigger.HOME -> {
                homeJob?.cancel()
                homeJob = job
            }

            ReminderTrigger.APP_EXIT -> {
                exitJob?.cancel()
                exitJob = job
            }

            ReminderTrigger.RECENT -> {
                recentJob?.cancel()
                recentJob = job
            }

            ReminderTrigger.AD_CLICK -> {
                adClickJob?.cancel()
                adClickJob = job
            }

            else -> job.cancel()
        }
    }
}

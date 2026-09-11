package com.ysdc.aidpdf.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.reminder.notice.ReminderNotificationCenter
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
import com.ysdc.aidpdf.tracking.AdjustInitializer
import com.ysdc.aidpdf.ui.MainActivity
import com.ysdc.aidpdf.ui.guide.LaunchLoadingActivity
import com.ysdc.aidpdf.ui.guide.LaunchRelayActivity

class AppVisibilityTracker(private val application: Application) : Application.ActivityLifecycleCallbacks {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val knownActivities = linkedSetOf<Activity>()
    private val visibleActivities = linkedSetOf<Activity>()
    private var restartWhenVisible = false
    private var skipRequestedAt = 0L
    private val confirmBackground = Runnable { commitBackgroundTransition() }

    fun register() {
        application.registerActivityLifecycleCallbacks(this)
    }

    fun skipNextHotStart() {
        skipRequestedAt = SystemClock.elapsedRealtime()
    }

    fun clearHotStartSkip() {
        skipRequestedAt = 0L
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        knownActivities.add(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        mainHandler.removeCallbacks(confirmBackground)
        ReminderNotificationCenter.stopSystemRefresh()
        visibleActivities.add(activity)
        ReminderTriggerCenter.onForegroundCountChanged(visibleActivities.size)
        if (!restartWhenVisible) return

        restartWhenVisible = false
        if (!screenIsInteractive() || activity is LaunchRelayActivity || activity is LaunchLoadingActivity) return
        routeThroughLoading(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        AdjustInitializer.onActivityResumed()
    }

    override fun onActivityPaused(activity: Activity) {
        AdjustInitializer.onActivityPaused()
    }

    override fun onActivityStopped(activity: Activity) {
        visibleActivities.remove(activity)
        if (visibleActivities.isNotEmpty()) {
            ReminderTriggerCenter.onForegroundCountChanged(visibleActivities.size)
            return
        }

        mainHandler.removeCallbacks(confirmBackground)
        val delayMillis = if (BlockUtils.shouldBlockAds(application)) {
            BLOCKED_USER_BACKGROUND_DELAY
        } else {
            BACKGROUND_DELAY
        }
        mainHandler.postDelayed(confirmBackground, delayMillis)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        knownActivities.remove(activity)
        visibleActivities.remove(activity)
    }

    private fun commitBackgroundTransition() {
        if (visibleActivities.isNotEmpty()) return
        if (consumeHotStartSkip()) {
            restartWhenVisible = false
            ReminderTriggerCenter.onForegroundCountChanged(0, triggerExit = false)
            return
        }
        ReminderTriggerCenter.onForegroundCountChanged(0)
        restartWhenVisible = true
        finishActivitiesOutsideLaunchFlow()
    }

    private fun routeThroughLoading(activity: Activity) {
        val relayIntent = Intent(activity, LaunchRelayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            latestLoadingIntent()?.extras?.let(::putExtras)
        }
        activity.startActivity(relayIntent)
        activity.finish()
    }

    private fun latestLoadingIntent(): Intent? {
        return knownActivities.filterIsInstance<LaunchLoadingActivity>().lastOrNull()?.intent
    }

    private fun finishActivitiesOutsideLaunchFlow() {
        knownActivities.toList().forEach { activity ->
            val isEntryActivity = activity is MainActivity ||
                    activity is LaunchLoadingActivity ||
                    activity is LaunchRelayActivity
            if (!isEntryActivity && !activity.isFinishing) {
                activity.finish()
            }
        }
    }

    private fun consumeHotStartSkip(): Boolean {
        val requestedAt = skipRequestedAt
        clearHotStartSkip()
        return requestedAt > 0L &&
                SystemClock.elapsedRealtime() - requestedAt <= SKIP_VALID_MILLIS
    }

    private fun screenIsInteractive(): Boolean {
        return runCatching {
            val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isInteractive
        }.getOrDefault(true)
    }

    private companion object {
        private const val BACKGROUND_DELAY = 300L
        private const val BLOCKED_USER_BACKGROUND_DELAY = 3_000L
        private const val SKIP_VALID_MILLIS = 15_000L
    }
}

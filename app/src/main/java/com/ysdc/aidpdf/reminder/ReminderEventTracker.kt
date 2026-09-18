package com.ysdc.aidpdf.reminder

import android.content.Intent
import android.util.Log
import com.ysdc.aidpdf.reminder.model.EXTRA_REMINDER_MEDIA2
import com.ysdc.aidpdf.reminder.model.EXTRA_REMINDER_SOURCE
import com.ysdc.aidpdf.reminder.model.EXTRA_REMINDER_TRIGGER
import com.ysdc.aidpdf.reminder.model.ReminderSource
import com.ysdc.aidpdf.reminder.model.ReminderTrigger
import com.ysdc.aidpdf.tracking.AidEventHub
import com.ysdc.aidpdf.tracking.TrackingEventNames

object ReminderEventTracker {

    private const val TAG = "ReminderEventTracker"

    fun reportDetectionPass(eventName: String) {
        AidEventHub.track(eventName)
    }

    fun reportTriggerSuccess() {
//        AidEventHub.track(TrackingEventNames.TRIGGER_NOTIFICATION_SUCCESS)
    }

    fun reportChannelSent(trigger: ReminderTrigger, source: ReminderSource) {
        if (source == ReminderSource.ALWAYS) {
            reportAlwaysTriggered()
            return
        }
        /*AidEventHub.track(
            trigger.sceneSendEvent(),
            mapOf("type" to source.value)
        )*/
        val triggerEvent = when (source) {
            ReminderSource.SYSTEM -> TrackingEventNames.SYSTEM_NOTIFICATION_TRIGGER
            ReminderSource.FLOATING -> TrackingEventNames.FLOATING_NOTIFICATION_TRIGGER
            ReminderSource.MEDIA -> TrackingEventNames.MEDIA_NOTIFICATION_TRIGGER
            ReminderSource.ALWAYS -> TrackingEventNames.ALWAYS_NOTIFICATION_TRIGGER
        }
        AidEventHub.track(triggerEvent, mapOf("type" to trigger.trackingValue))
    }

    fun reportFloatingView() {
        AidEventHub.track(TrackingEventNames.FLOATING_NOTIFICATION_VIEW)
    }

    fun reportAlwaysTriggered() {
        AidEventHub.track(TrackingEventNames.ALWAYS_NOTIFICATION_TRIGGER)
    }

    fun reportClick(intent: Intent?) {
        val source = ReminderSource.fromValue(intent?.getStringExtra(EXTRA_REMINDER_SOURCE)) ?: return
        val isMedia2 = intent?.getBooleanExtra(EXTRA_REMINDER_MEDIA2, false) == true
        val trigger = intent?.getStringExtra(EXTRA_REMINDER_TRIGGER)
            ?.let { value -> ReminderTrigger.entries.firstOrNull { it.name == value } }
        val clickEvent = when (source) {
            ReminderSource.SYSTEM -> TrackingEventNames.SYSTEM_NOTIFICATION_CLICK
            ReminderSource.FLOATING -> TrackingEventNames.FLOATING_NOTIFICATION_CLICK
            ReminderSource.MEDIA -> {
                // 媒体通知复用了同一 source，这里依赖额外标记区分新媒体2点击事件
                if (isMedia2) TrackingEventNames.MEDIA2_NOTIFICATION_CLICK
                else TrackingEventNames.MEDIA_NOTIFICATION_CLICK
            }
            ReminderSource.ALWAYS -> TrackingEventNames.ALWAYS_NOTIFICATION_CLICK
        }
        val parameters = trigger?.let { mapOf("scene" to it.trackingValue) }.orEmpty()
        if (source == ReminderSource.MEDIA) {
            Log.d(TAG, "媒体通知点击埋点：isMedia2=$isMedia2, event=$clickEvent, trigger=${trigger?.name}")
        }
        AidEventHub.track(clickEvent, parameters)
    }

    fun source(intent: Intent?): ReminderSource? {
        return ReminderSource.fromValue(intent?.getStringExtra(EXTRA_REMINDER_SOURCE))
    }

    fun triggerScene(intent: Intent?): String? {
        return intent?.getStringExtra(EXTRA_REMINDER_TRIGGER)
            ?.let { value -> ReminderTrigger.entries.firstOrNull { it.name == value } }
            ?.trackingValue
    }

    /*private fun ReminderTrigger.sceneSendEvent(): String = when (this) {
        ReminderTrigger.TIME -> TrackingEventNames.TIME_SCENE_SEND
        ReminderTrigger.UNLOCK -> TrackingEventNames.UNLOCK_SCENE_SEND
        ReminderTrigger.ALARM -> TrackingEventNames.ALARM_SCENE_SEND
        ReminderTrigger.HOME -> TrackingEventNames.HOME_SCENE_SEND
        ReminderTrigger.APP_EXIT -> TrackingEventNames.EXIT_SCENE_SEND
        ReminderTrigger.RECENT -> TrackingEventNames.RECENT_SCENE_SEND
        ReminderTrigger.AD_CLICK -> TrackingEventNames.AD_CLICK_SCENE_SEND
    }*/
}

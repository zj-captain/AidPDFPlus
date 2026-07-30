package com.ysdc.aidpdf.reminder.store

import com.ysdc.aidpdf.reminder.model.ReminderTrigger
import com.ysdc.aidpdf.reminder.notice.ReminderSystemNoticeIds
import com.ysdc.aidpdf.store.adClickReminderContentIndex
import com.ysdc.aidpdf.store.adClickReminderCount
import com.ysdc.aidpdf.store.adClickReminderDay
import com.ysdc.aidpdf.store.adClickReminderLastShownAt
import com.ysdc.aidpdf.store.additionalReminderSystemNoticeSlot
import com.ysdc.aidpdf.store.alarmReminderContentIndex
import com.ysdc.aidpdf.store.alarmReminderCount
import com.ysdc.aidpdf.store.alarmReminderDay
import com.ysdc.aidpdf.store.alarmReminderLastShownAt
import com.ysdc.aidpdf.store.alarmReminderSystemNoticeSlot
import com.ysdc.aidpdf.store.appExitReminderContentIndex
import com.ysdc.aidpdf.store.appExitReminderCount
import com.ysdc.aidpdf.store.appExitReminderDay
import com.ysdc.aidpdf.store.appExitReminderLastShownAt
import com.ysdc.aidpdf.store.homeReminderContentIndex
import com.ysdc.aidpdf.store.homeReminderCount
import com.ysdc.aidpdf.store.homeReminderDay
import com.ysdc.aidpdf.store.homeReminderLastShownAt
import com.ysdc.aidpdf.store.recentReminderContentIndex
import com.ysdc.aidpdf.store.recentReminderCount
import com.ysdc.aidpdf.store.recentReminderDay
import com.ysdc.aidpdf.store.recentReminderLastShownAt
import com.ysdc.aidpdf.store.timeReminderContentIndex
import com.ysdc.aidpdf.store.timeReminderCount
import com.ysdc.aidpdf.store.timeReminderDay
import com.ysdc.aidpdf.store.timeReminderLastShownAt
import com.ysdc.aidpdf.store.timeReminderSystemNoticeSlot
import com.ysdc.aidpdf.store.unlockReminderContentIndex
import com.ysdc.aidpdf.store.unlockReminderCount
import com.ysdc.aidpdf.store.unlockReminderDay
import com.ysdc.aidpdf.store.unlockReminderLastShownAt
import com.ysdc.aidpdf.store.unlockReminderSystemNoticeSlot
import java.util.Calendar
import kotlin.reflect.KMutableProperty0

data class ReminderSceneStats(val count: Int, val lastShownAt: Long)

object ReminderStatsStore {

    private val scenePreferences = mapOf(
        ReminderTrigger.TIME to ScenePreferences(::timeReminderDay, ::timeReminderCount, ::timeReminderLastShownAt),
        ReminderTrigger.UNLOCK to ScenePreferences(::unlockReminderDay, ::unlockReminderCount, ::unlockReminderLastShownAt),
        ReminderTrigger.ALARM to ScenePreferences(::alarmReminderDay, ::alarmReminderCount, ::alarmReminderLastShownAt),
        ReminderTrigger.HOME to ScenePreferences(::homeReminderDay, ::homeReminderCount, ::homeReminderLastShownAt),
        ReminderTrigger.APP_EXIT to ScenePreferences(::appExitReminderDay, ::appExitReminderCount, ::appExitReminderLastShownAt),
        ReminderTrigger.RECENT to ScenePreferences(::recentReminderDay, ::recentReminderCount, ::recentReminderLastShownAt),
        ReminderTrigger.AD_CLICK to ScenePreferences(::adClickReminderDay, ::adClickReminderCount, ::adClickReminderLastShownAt)
    )
    private val contentIndexes = mapOf(
        ReminderTrigger.TIME to ::timeReminderContentIndex,
        ReminderTrigger.UNLOCK to ::unlockReminderContentIndex,
        ReminderTrigger.ALARM to ::alarmReminderContentIndex,
        ReminderTrigger.HOME to ::homeReminderContentIndex,
        ReminderTrigger.APP_EXIT to ::appExitReminderContentIndex,
        ReminderTrigger.RECENT to ::recentReminderContentIndex,
        ReminderTrigger.AD_CLICK to ::adClickReminderContentIndex
    )
    private val systemNoticeSlots = mapOf(
        ReminderTrigger.TIME to ::timeReminderSystemNoticeSlot,
        ReminderTrigger.UNLOCK to ::unlockReminderSystemNoticeSlot,
        ReminderTrigger.ALARM to ::alarmReminderSystemNoticeSlot,
        ReminderTrigger.HOME to ::additionalReminderSystemNoticeSlot,
        ReminderTrigger.APP_EXIT to ::additionalReminderSystemNoticeSlot,
        ReminderTrigger.RECENT to ::additionalReminderSystemNoticeSlot,
        ReminderTrigger.AD_CLICK to ::additionalReminderSystemNoticeSlot
    )

    fun read(trigger: ReminderTrigger, now: Long = System.currentTimeMillis()): ReminderSceneStats {
        val preferences = scenePreferences.getValue(trigger)
        val count = if (preferences.day.get() == dayKey(now)) preferences.count.get() else 0
        return ReminderSceneStats(count, preferences.lastShownAt.get())
    }

    @Synchronized
    fun record(trigger: ReminderTrigger, now: Long = System.currentTimeMillis()) {
        val preferences = scenePreferences.getValue(trigger)
        val today = dayKey(now)
        val oldCount = if (preferences.day.get() == today) preferences.count.get() else 0
        preferences.day.set(today)
        preferences.count.set(oldCount + 1)
        preferences.lastShownAt.set(now)
    }

    @Synchronized
    fun nextContentIndex(contentGroup: ReminderTrigger, size: Int): Int {
        if (size <= 0) return 0
        val preference = contentIndexes.getValue(contentGroup)
        val index = nextRandomContentIndex(preference.get(), size)
        preference.set(index)
        return index
    }

    @Synchronized
    fun nextSystemNoticeSlot(trigger: ReminderTrigger): Int {
        val preference = systemNoticeSlots.getValue(trigger)
        val size = ReminderSystemNoticeIds.slotCount(trigger)
        val slot = preference.get().floorMod(size)
        preference.set((slot + 1).floorMod(size))
        return slot
    }

    private fun dayKey(timeMillis: Long): Int {
        return Calendar.getInstance().apply { timeInMillis = timeMillis }
            .let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
    }

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

    private data class ScenePreferences(
        val day: KMutableProperty0<Int>,
        val count: KMutableProperty0<Int>,
        val lastShownAt: KMutableProperty0<Long>
    )
}

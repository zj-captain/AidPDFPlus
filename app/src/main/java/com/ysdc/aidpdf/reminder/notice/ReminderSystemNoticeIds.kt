package com.ysdc.aidpdf.reminder.notice

import com.ysdc.aidpdf.reminder.model.ReminderTrigger

object ReminderSystemNoticeIds {

    private const val BASE_ID = 24_100
    private const val ORIGINAL_SCENE_SLOT_COUNT = 2
    private const val ADDITIONAL_SCENE_SLOT_COUNT = 4
    private const val ADDITIONAL_SCENE_OFFSET = 6

    fun slotCount(trigger: ReminderTrigger): Int {
        return if (trigger.isAdditionalScene) {
            ADDITIONAL_SCENE_SLOT_COUNT
        } else {
            ORIGINAL_SCENE_SLOT_COUNT
        }
    }

    fun idFor(trigger: ReminderTrigger, slot: Int): Int {
        val normalizedSlot = slot.floorMod(slotCount(trigger))
        val offset = when (trigger) {
            ReminderTrigger.TIME -> 0
            ReminderTrigger.UNLOCK -> 2
            ReminderTrigger.ALARM -> 4
            else -> ADDITIONAL_SCENE_OFFSET
        }
        return BASE_ID + offset + normalizedSlot
    }

    fun allIds(): IntRange = BASE_ID until BASE_ID + 10

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
}

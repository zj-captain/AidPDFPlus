package com.ysdc.aidpdf.reminder.content

import ads_mobile_sdk.`is`
import android.content.Context
import androidx.annotation.ArrayRes
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.reminder.model.ReminderContent
import com.ysdc.aidpdf.reminder.model.ReminderMessage
import com.ysdc.aidpdf.reminder.model.ReminderTarget
import com.ysdc.aidpdf.reminder.model.ReminderTrigger
import com.ysdc.aidpdf.reminder.store.ReminderStatsStore

object ReminderContentPool {

    fun next(context: Context, trigger: ReminderTrigger): ReminderMessage {
        val contentGroup = sourceTrigger(trigger)
        val texts = context.resources.getStringArray(textArrayFor(contentGroup))
        val size = minOf(texts.size, TARGET_ORDER.size)
        check(size > 0) { "Reminder content arrays must not be empty" }
        val index = ReminderStatsStore.nextContentIndex(contentGroup, size)
        val target = TARGET_ORDER[index]
        return ReminderMessage(
            trigger = trigger,
            content = ReminderContent(
                target = target,
                text = texts[index],
                button = context.getString(R.string.reminder_action_check),
                imageRes = imageFor(target)
            )
        )
    }

    fun sourceTrigger(trigger: ReminderTrigger): ReminderTrigger = when (trigger) {
        ReminderTrigger.HOME, ReminderTrigger.RECENT -> ReminderTrigger.TIME
        ReminderTrigger.APP_EXIT -> ReminderTrigger.ALARM
        ReminderTrigger.AD_CLICK -> ReminderTrigger.UNLOCK
        else -> trigger
    }

    @ArrayRes
    private fun textArrayFor(trigger: ReminderTrigger): Int = when (trigger) {
        ReminderTrigger.TIME -> R.array.reminder_time_messages
        ReminderTrigger.UNLOCK -> R.array.reminder_unlock_messages
        ReminderTrigger.ALARM -> R.array.reminder_alarm_messages
        else -> R.array.reminder_time_messages
    }

    private fun imageFor(target: ReminderTarget): Int = when (target) {
        ReminderTarget.PDF -> R.drawable.img_notice_pdf
        ReminderTarget.WORD -> R.drawable.img_notice_word
        ReminderTarget.EXCEL -> R.drawable.img_notice_excel
        ReminderTarget.PPT -> R.drawable.img_notice_ppt
    }

    private val TARGET_ORDER = arrayOf(
        ReminderTarget.WORD,
        ReminderTarget.WORD,
        ReminderTarget.EXCEL,
        ReminderTarget.EXCEL,
        ReminderTarget.PPT,
        ReminderTarget.PPT,
        ReminderTarget.PDF,
        ReminderTarget.PDF
    )
}

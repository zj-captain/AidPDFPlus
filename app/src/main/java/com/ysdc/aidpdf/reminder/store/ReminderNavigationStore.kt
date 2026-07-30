package com.ysdc.aidpdf.reminder.store

import android.content.Intent
import com.ysdc.aidpdf.reminder.model.EXTRA_REMINDER_SOURCE
import com.ysdc.aidpdf.reminder.model.EXTRA_REMINDER_TARGET
import com.ysdc.aidpdf.reminder.model.EXTRA_REMINDER_TRIGGER
import com.ysdc.aidpdf.reminder.model.ReminderSource
import com.ysdc.aidpdf.store.pendingReminderSource
import com.ysdc.aidpdf.store.pendingReminderTarget
import com.ysdc.aidpdf.store.pendingReminderTrigger

data class PendingReminderNavigation(
    val target: String,
    val trigger: String?,
    val source: ReminderSource?
)

object ReminderNavigationStore {

    fun capture(intent: Intent?) {
        val target = intent?.getStringExtra(EXTRA_REMINDER_TARGET).orEmpty()
        if (target.isBlank()) return
        pendingReminderTarget = target
        pendingReminderTrigger = intent?.getStringExtra(EXTRA_REMINDER_TRIGGER).orEmpty()
        pendingReminderSource = intent?.getStringExtra(EXTRA_REMINDER_SOURCE).orEmpty()
    }

    fun consume(): PendingReminderNavigation? {
        val target = pendingReminderTarget
        if (target.isBlank()) return null
        val result = PendingReminderNavigation(
            target = target,
            trigger = pendingReminderTrigger.ifBlank { null },
            source = ReminderSource.fromValue(pendingReminderSource)
        )
        pendingReminderTarget = ""
        pendingReminderTrigger = ""
        pendingReminderSource = ""
        return result
    }
}

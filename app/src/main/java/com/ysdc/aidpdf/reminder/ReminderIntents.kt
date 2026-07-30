package com.ysdc.aidpdf.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ysdc.aidpdf.reminder.model.EXTRA_REMINDER_TARGET
import com.ysdc.aidpdf.reminder.model.EXTRA_REMINDER_TRIGGER
import com.ysdc.aidpdf.reminder.model.EXTRA_REMINDER_NOTICE_ID
import com.ysdc.aidpdf.reminder.model.EXTRA_REMINDER_SOURCE
import com.ysdc.aidpdf.reminder.model.ReminderTarget
import com.ysdc.aidpdf.reminder.model.ReminderSource
import com.ysdc.aidpdf.reminder.model.ReminderTrigger
import com.ysdc.aidpdf.ui.guide.LaunchLoadingActivity

object ReminderIntents {

    fun openIntent(
        context: Context,
        target: ReminderTarget,
        trigger: ReminderTrigger? = null,
        noticeId: Int? = null,
        source: ReminderSource? = null,
    ): Intent {
        return Intent(context, LaunchLoadingActivity::class.java).apply {
            putExtra(EXTRA_REMINDER_TARGET, target.value)
            trigger?.let { putExtra(EXTRA_REMINDER_TRIGGER, it.name) }
            noticeId?.let { putExtra(EXTRA_REMINDER_NOTICE_ID, it) }
            source?.let { putExtra(EXTRA_REMINDER_SOURCE, it.value) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    fun pendingOpenIntent(
        context: Context,
        requestCode: Int,
        target: ReminderTarget,
        trigger: ReminderTrigger? = null,
        noticeId: Int? = null,
        source: ReminderSource? = null,
    ): PendingIntent {
        return PendingIntent.getActivity(
            context,
            requestCode,
            openIntent(context, target, trigger, noticeId, source),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

package com.ysdc.aidpdf.reminder.overlay

import android.annotation.SuppressLint
import android.content.Context
import com.ysdc.aidpdf.ad.gate.OpenAdGate
import com.ysdc.aidpdf.reminder.model.ReminderMessage
import com.ysdc.aidpdf.reminder.notice.ReminderNotificationCenter

object ReminderOverlayController {

    @SuppressLint("StaticFieldLeak")
    private var window: ReminderOverlayWindow? = null

    @Synchronized
    fun show(context: Context, message: ReminderMessage): Boolean {
        if (isShowing()) return false
        ReminderNotificationCenter.stopSystemRefresh()
        val firstDisplay = ReminderOverlayPolicy.isFirstDisplay()
//        if (firstDisplay) {
//            runCatching { OpenAdGate.prepare(context.applicationContext) }
//        }
        val overlay = window ?: ReminderOverlayWindow(context).also { window = it }
        return overlay.show(message, firstDisplay).also { shown ->
            if (shown && firstDisplay) ReminderOverlayPolicy.markDisplayed()
        }
    }

    fun hide() {
        window?.hide()
    }

    fun isShowing(): Boolean = window?.isShowing() == true
}

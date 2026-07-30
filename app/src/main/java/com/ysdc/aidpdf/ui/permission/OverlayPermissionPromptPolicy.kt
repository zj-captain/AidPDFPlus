package com.ysdc.aidpdf.ui.permission

import com.ysdc.aidpdf.reminder.model.ReminderSource

internal object OverlayPermissionPromptPolicy {

    fun skipForReminder(source: ReminderSource?): Boolean = source != null

    fun shouldShowLaunchPage(
        source: ReminderSource?,
        launchedFromAppIcon: Boolean,
        forceOpenAdLaunch: Boolean,
        canDrawOverlays: Boolean,
        adsBlocked: Boolean
    ): Boolean {
        return !skipForReminder(source) &&
                (launchedFromAppIcon || forceOpenAdLaunch) &&
                !canDrawOverlays &&
                !adsBlocked
    }
}

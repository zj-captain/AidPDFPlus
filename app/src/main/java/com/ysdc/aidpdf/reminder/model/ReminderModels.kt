package com.ysdc.aidpdf.reminder.model

import androidx.annotation.DrawableRes

const val EXTRA_REMINDER_TARGET = "extra_reminder_target"
const val EXTRA_REMINDER_TRIGGER = "extra_reminder_trigger"
const val EXTRA_REMINDER_NOTICE_ID = "extra_reminder_notice_id"
const val EXTRA_REMINDER_SOURCE = "extra_reminder_source"
const val EXTRA_REMINDER_MEDIA2 = "extra_reminder_media2"

enum class ReminderTrigger {
    TIME,
    UNLOCK,
    ALARM,
    HOME,
    APP_EXIT,
    RECENT,
    AD_CLICK;

    val isAdditionalScene: Boolean
        get() = this == HOME || this == APP_EXIT || this == RECENT || this == AD_CLICK

    val requiresInteractiveScreen: Boolean
        get() = this != TIME && this != ALARM

    val trackingValue: String
        get() = when (this) {
            TIME -> "time"
            UNLOCK -> "unlock"
            ALARM -> "alarm"
            HOME -> "home"
            APP_EXIT -> "exit"
            RECENT -> "recent"
            AD_CLICK -> "adclick"
        }
}

enum class ReminderSource(val value: String) {
    SYSTEM("system"),
    FLOATING("floating"),
    MEDIA("media"),
    ALWAYS("always");

    companion object {
        fun fromValue(value: String?): ReminderSource? {
            return entries.firstOrNull { it.value == value }
        }
    }
}

enum class ReminderTarget(val value: String) {
    PDF("pdf"),
    WORD("word"),
    EXCEL("excel"),
    PPT("ppt");

    companion object {
        fun fromValue(value: String?): ReminderTarget? {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
        }
    }
}

data class ReminderContent(
    val target: ReminderTarget,
    val text: String,
    val button: String,
    @DrawableRes val imageRes: Int,
)

data class ReminderMessage(
    val trigger: ReminderTrigger,
    val content: ReminderContent,
    var isMedia: Boolean = false
)

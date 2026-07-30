package com.ysdc.aidpdf.reminder.config

import android.os.Build
import com.ysdc.aidpdf.reminder.model.ReminderTrigger
import org.json.JSONObject

data class BaseReminderConfig(
    val quietStartHour: Int,
    val quietEndHour: Int,
    val enabled: Boolean,
    val timeIntervalMinutes: Int,
    val timeDailyLimit: Int,
    val unlockIntervalMinutes: Int,
    val unlockDailyLimit: Int,
    val alarmIntervalMinutes: Int,
    val alarmDailyLimit: Int,
)

data class AdditionalReminderConfig(
    val enabled: Boolean,
    val homeIntervalMinutes: Int,
    val homeDailyLimit: Int,
    val homeDelaySeconds: Int,
    val exitIntervalMinutes: Int,
    val exitDailyLimit: Int,
    val exitDelaySeconds: Int,
    val recentIntervalMinutes: Int,
    val recentDailyLimit: Int,
    val recentDelaySeconds: Int,
    val adClickIntervalMinutes: Int,
    val adClickDailyLimit: Int,
    val adClickDelaySeconds: Int,
)

data class ReminderConfig(
    val base: BaseReminderConfig,
    val additional: AdditionalReminderConfig,
) {
    fun intervalMinutes(trigger: ReminderTrigger): Int = when (trigger) {
        ReminderTrigger.TIME -> base.timeIntervalMinutes
        ReminderTrigger.UNLOCK -> base.unlockIntervalMinutes
        ReminderTrigger.ALARM -> base.alarmIntervalMinutes
        ReminderTrigger.HOME -> additional.homeIntervalMinutes
        ReminderTrigger.APP_EXIT -> additional.exitIntervalMinutes
        ReminderTrigger.RECENT -> additional.recentIntervalMinutes
        ReminderTrigger.AD_CLICK -> additional.adClickIntervalMinutes
    }

    fun dailyLimit(trigger: ReminderTrigger): Int = when (trigger) {
        ReminderTrigger.TIME -> base.timeDailyLimit
        ReminderTrigger.UNLOCK -> base.unlockDailyLimit
        ReminderTrigger.ALARM -> base.alarmDailyLimit
        ReminderTrigger.HOME -> additional.homeDailyLimit
        ReminderTrigger.APP_EXIT -> additional.exitDailyLimit
        ReminderTrigger.RECENT -> additional.recentDailyLimit
        ReminderTrigger.AD_CLICK -> additional.adClickDailyLimit
    }

    fun delaySeconds(trigger: ReminderTrigger): Int = when (trigger) {
        ReminderTrigger.HOME -> additional.homeDelaySeconds
        ReminderTrigger.APP_EXIT -> additional.exitDelaySeconds
        ReminderTrigger.RECENT -> additional.recentDelaySeconds
        ReminderTrigger.AD_CLICK -> additional.adClickDelaySeconds
        else -> 0
    }

    fun isEnabled(trigger: ReminderTrigger): Boolean {
        return base.enabled && (!trigger.isAdditionalScene || additional.enabled)
    }
}

object ReminderQuietHours {
    fun contains(hourOfDay: Int, startHour: Int, endHour: Int): Boolean {
        val hour = hourOfDay.coerceIn(0, 23)
        val start = normalize(startHour)
        val end = normalize(endHour)
        if (start == end) return false
        return if (start < end) hour in start until end else hour !in end..<start
    }

    private fun normalize(hour: Int): Int = hour.coerceIn(0, 24) % 24
}

object ReminderConfigRepository {

    const val STANDARD_BASE_KEY = "acpop_config"
    const val SAMSUNG_BASE_KEY = "samsung_ac_pop"
    const val STANDARD_ADDITIONAL_KEY = "new_config"
    const val SAMSUNG_ADDITIONAL_KEY = "samsung_new_config"

    @Volatile
    var current: ReminderConfig = localDefaults()
        private set

    val isSamsung: Boolean
        get() = Build.MANUFACTURER.orEmpty().contains("samsung", ignoreCase = true)

    fun resetToLocalDefaults() {
        current = localDefaults()
    }

    fun applyRemote(baseJson: String, additionalJson: String) {
        val defaults = localDefaults()
        current = ReminderConfig(
            base = parseBase(baseJson, defaults.base),
            additional = parseAdditional(additionalJson, defaults.additional)
        )
    }

    fun defaultJsonValues(): Map<String, String> = mapOf(
        STANDARD_BASE_KEY to baseJson(samsung = false),
        SAMSUNG_BASE_KEY to baseJson(samsung = true),
        STANDARD_ADDITIONAL_KEY to additionalJson(samsung = false),
        SAMSUNG_ADDITIONAL_KEY to additionalJson(samsung = true)
    )

    private fun localDefaults(): ReminderConfig {
        val samsung = isSamsung
        return ReminderConfig(
            base = BaseReminderConfig(
                quietStartHour = 24,
                quietEndHour = 6,
                enabled = true,
                timeIntervalMinutes = 30,
                timeDailyLimit = if (samsung) 15 else 30,
                unlockIntervalMinutes = 0,
                unlockDailyLimit = if (samsung) 20 else 40,
                alarmIntervalMinutes = 30,
                alarmDailyLimit = if (samsung) 20 else 40
            ),
            additional = AdditionalReminderConfig(
                enabled = true,
                homeIntervalMinutes = 30,
                homeDailyLimit = if (samsung) 20 else 40,
                homeDelaySeconds = 2,
                exitIntervalMinutes = 30,
                exitDailyLimit = if (samsung) 20 else 40,
                exitDelaySeconds = 3,
                recentIntervalMinutes = 30,
                recentDailyLimit = if (samsung) 20 else 40,
                recentDelaySeconds = 5,
                adClickIntervalMinutes = 30,
                adClickDailyLimit = if (samsung) 20 else 40,
                adClickDelaySeconds = 2
            )
        )
    }

    private fun parseBase(raw: String, fallback: BaseReminderConfig): BaseReminderConfig {
        if (raw.isBlank()) return fallback
        return runCatching {
            val json = JSONObject(raw)
            BaseReminderConfig(
                quietStartHour = json.nonNegative("nopop_start", fallback.quietStartHour).coerceAtMost(24),
                quietEndHour = json.nonNegative("nopop_end", fallback.quietEndHour).coerceAtMost(24),
                enabled = json.optInt("ac_on", if (fallback.enabled) 1 else 0) == 1,
                timeIntervalMinutes = json.nonNegative("ac_t", fallback.timeIntervalMinutes),
                timeDailyLimit = json.nonNegative("ac_t_limit", fallback.timeDailyLimit),
                unlockIntervalMinutes = json.nonNegative("ac_u", fallback.unlockIntervalMinutes),
                unlockDailyLimit = json.nonNegative("ac_u_limit", fallback.unlockDailyLimit),
                alarmIntervalMinutes = json.nonNegative("ac_a", fallback.alarmIntervalMinutes),
                alarmDailyLimit = json.nonNegative("ac_a_limit", fallback.alarmDailyLimit)
            )
        }.getOrDefault(fallback)
    }

    private fun parseAdditional(raw: String, fallback: AdditionalReminderConfig): AdditionalReminderConfig {
        if (raw.isBlank()) return fallback
        return runCatching {
            val json = JSONObject(raw)
            AdditionalReminderConfig(
                enabled = json.optInt("ac_on", if (fallback.enabled) 1 else 0) == 1,
                homeIntervalMinutes = json.nonNegative("ac_home", fallback.homeIntervalMinutes),
                homeDailyLimit = json.nonNegative("ac_home_limit", fallback.homeDailyLimit),
                homeDelaySeconds = json.nonNegative("home_delay", fallback.homeDelaySeconds),
                exitIntervalMinutes = json.nonNegative("ac_exit", fallback.exitIntervalMinutes),
                exitDailyLimit = json.nonNegative("ac_exit_limit", fallback.exitDailyLimit),
                exitDelaySeconds = json.nonNegative("exit_delay", fallback.exitDelaySeconds),
                recentIntervalMinutes = json.nonNegative("ac_recent", fallback.recentIntervalMinutes),
                recentDailyLimit = json.nonNegative("ac_recent_limit", fallback.recentDailyLimit),
                recentDelaySeconds = json.nonNegative("recent_delay", fallback.recentDelaySeconds),
                adClickIntervalMinutes = json.nonNegative("ac_adclick", fallback.adClickIntervalMinutes),
                adClickDailyLimit = json.nonNegative("ac_adclick_limit", fallback.adClickDailyLimit),
                adClickDelaySeconds = json.nonNegative("adclick_delay", fallback.adClickDelaySeconds)
            )
        }.getOrDefault(fallback)
    }

    private fun JSONObject.nonNegative(key: String, fallback: Int): Int {
        return optInt(key, fallback).coerceAtLeast(0)
    }

    private fun baseJson(samsung: Boolean): String {
        return if (samsung) {
            """{"nopop_start":24,"nopop_end":6,"ac_on":1,"ac_t":30,"ac_t_limit":15,"ac_u":5,"ac_u_limit":20,"ac_a":30,"ac_a_limit":20}"""
        } else {
            """{"nopop_start":24,"nopop_end":6,"ac_on":1,"ac_t":30,"ac_t_limit":30,"ac_u":5,"ac_u_limit":40,"ac_a":30,"ac_a_limit":40}"""
        }
    }

    private fun additionalJson(samsung: Boolean): String {
        val limit = if (samsung) 20 else 40
        return """{"ac_on":1,"ac_home":30,"ac_home_limit":$limit,"home_delay":2,"ac_exit":30,"ac_exit_limit":$limit,"exit_delay":3,"ac_recent":30,"ac_recent_limit":$limit,"recent_delay":5,"ac_adclick":30,"ac_adclick_limit":$limit,"adclick_delay":2}"""
    }
}

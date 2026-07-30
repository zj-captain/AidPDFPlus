package com.ysdc.aidpdf.reminder.config

import org.json.JSONObject

data class ReminderOverlayConfig(
    val accidentalJumpEnabled: Boolean,
    val jumpPercent: Int,
    val firstIntervalMinutes: Int,
) {
    fun shouldJumpFromClose(randomPercent: Int): Boolean {
        return accidentalJumpEnabled && randomPercent.coerceIn(0, 99) < jumpPercent
    }
}

object ReminderOverlayConfigRepository {

    const val REMOTE_KEY = "winpop_jump_config"

    private const val DEFAULT_JSON =
        """{"winpop_jump_switch":0,"jump_percent":50,"first_interval":5}"""

    @Volatile
    var current: ReminderOverlayConfig = localDefaults()
        private set

    fun resetToLocalDefaults() {
        current = localDefaults()
    }

    fun applyRemote(raw: String) {
        current = parse(raw, localDefaults())
    }

    fun defaultJsonValues(): Map<String, String> = mapOf(REMOTE_KEY to DEFAULT_JSON)

    private fun parse(raw: String, fallback: ReminderOverlayConfig): ReminderOverlayConfig {
        if (raw.isBlank()) return fallback
        return runCatching {
            val json = JSONObject(raw)
            ReminderOverlayConfig(
                accidentalJumpEnabled = json.optInt(
                    "winpop_jump_switch",
                    if (fallback.accidentalJumpEnabled) 1 else 0
                ) == 1,
                jumpPercent = json.optInt("jump_percent", fallback.jumpPercent).coerceIn(0, 100),
                firstIntervalMinutes = json.optInt(
                    "first_interval",
                    fallback.firstIntervalMinutes
                ).coerceAtLeast(0)
            )
        }.getOrDefault(fallback)
    }

    private fun localDefaults(): ReminderOverlayConfig {
        return ReminderOverlayConfig(
            accidentalJumpEnabled = false,
            jumpPercent = 50,
            firstIntervalMinutes = 5
        )
    }
}

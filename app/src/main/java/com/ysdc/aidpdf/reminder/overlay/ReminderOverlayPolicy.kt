package com.ysdc.aidpdf.reminder.overlay

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ysdc.aidpdf.reminder.config.ReminderOverlayConfigRepository
import com.ysdc.aidpdf.store.hasShownReminderOverlay
import kotlin.random.Random

object ReminderOverlayPolicy {

    private const val MINUTE_MILLIS = 60_000L

    fun isFirstDisplay(): Boolean = !hasShownReminderOverlay

    fun markDisplayed() {
        hasShownReminderOverlay = true
    }

    fun firstIntervalPassed(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val firstInstallAt = runCatching { context.firstInstallTime() }.getOrDefault(0L)
        return firstIntervalPassed(
            hasShown = hasShownReminderOverlay,
            firstInstallAt = firstInstallAt,
            now = now,
            intervalMinutes = ReminderOverlayConfigRepository.current.firstIntervalMinutes
        )
    }

    fun shouldJumpFromClose(firstDisplay: Boolean): Boolean {
        if (firstDisplay) return true
        return ReminderOverlayConfigRepository.current.shouldJumpFromClose(Random.nextInt(100))
    }

    internal fun firstIntervalPassed(
        hasShown: Boolean,
        firstInstallAt: Long,
        now: Long,
        intervalMinutes: Int,
    ): Boolean {
        if (hasShown || intervalMinutes <= 0 || firstInstallAt <= 0L) return true
        return now - firstInstallAt >= intervalMinutes * MINUTE_MILLIS
    }

    private fun Context.firstInstallTime(): Long {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        return packageInfo.firstInstallTime
    }
}

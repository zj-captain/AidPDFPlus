package com.ysdc.aidpdf.reminder

import android.content.Context
import android.os.Build
import android.os.PowerManager

object ReminderDeviceCompat {

    fun isAndroid12AndAbove(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun isAndroid16AndAbove(): Boolean = Build.VERSION.SDK_INT >= 36

    fun isSamsung(): Boolean = Build.MANUFACTURER.contains("samsung", ignoreCase = true)

    fun isOnePlus(): Boolean {
        return Build.MANUFACTURER.equals("oneplus", ignoreCase = true) ||
            Build.BRAND.equals("oneplus", ignoreCase = true)
    }

    fun isXiaomi(): Boolean {
        return Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("redmi", ignoreCase = true) ||
            Build.BRAND.equals("poco", ignoreCase = true)
    }

    fun isOneNoticeDevice(): Boolean {
        return isXiaomi() ||
            Build.MANUFACTURER.equals("FCNT", ignoreCase = true) ||
            Build.MANUFACTURER.equals("SHARP", ignoreCase = true) ||
            (
                Build.BRAND.equals("google", ignoreCase = true) &&
                    Build.MANUFACTURER.equals("google", ignoreCase = true)
                )
    }

    fun isScreenInteractive(context: Context): Boolean {
        return runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        }.getOrDefault(true)
    }
}

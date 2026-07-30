package com.ysdc.aidpdf.core.block

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import com.ysdc.aidpdf.store.firstConsentCountryCode
import java.util.Locale

internal object DeviceSignals {

    fun isSamsung(): Boolean {
        return Build.MANUFACTURER.contains("samsung", ignoreCase = true)
    }

    fun countryCode(): String {
        return firstConsentCountryCode
            .ifBlank { Locale.getDefault().country }
            .uppercase(Locale.US)
    }

    fun hasNoSim(context: Context): Boolean {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return true
        if (telephony.phoneType == TelephonyManager.PHONE_TYPE_NONE) return true
        return telephony.simState == TelephonyManager.SIM_STATE_ABSENT
    }

    fun isAdbEnabled(context: Context): Boolean {
        return runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)
    }

    fun isEmulator(): Boolean {
        val brand = Build.BRAND.orEmpty()
        val device = Build.DEVICE.orEmpty()
        val fingerprint = Build.FINGERPRINT.orEmpty()
        val hardware = Build.HARDWARE.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val product = Build.PRODUCT.orEmpty()

        return (brand.startsWith("generic") && device.startsWith("generic")) ||
            fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            hardware.contains("goldfish", ignoreCase = true) ||
            hardware.contains("ranchu", ignoreCase = true) ||
            hardware.contains("vbox", ignoreCase = true) ||
            manufacturer.contains("Genymotion", ignoreCase = true) ||
            model.contains("google_sdk", ignoreCase = true) ||
            model.contains("Emulator", ignoreCase = true) ||
            model.contains("Android SDK built for", ignoreCase = true) ||
            product.contains("sdk", ignoreCase = true) ||
            product.contains("emulator", ignoreCase = true) ||
            product.contains("simulator", ignoreCase = true)
    }
}

package com.ysdc.aidpdf.core.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

fun Context.canPostNotifications(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(this).areNotificationsEnabled()
    }
}

fun Context.canDrawOverlays(): Boolean {
    return Settings.canDrawOverlays(this)
}

fun Context.notificationSettingsIntent(): Intent {
    return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }
}

fun Context.overlaySettingsIntent(): Intent {
    return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
        data = "package:$packageName".toUri()
        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
    }
}

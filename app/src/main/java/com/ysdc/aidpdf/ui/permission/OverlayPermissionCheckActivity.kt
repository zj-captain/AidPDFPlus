package com.ysdc.aidpdf.ui.permission

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.ysdc.aidpdf.App
import com.ysdc.aidpdf.core.permission.canDrawOverlays
import com.ysdc.aidpdf.core.permission.overlaySettingsIntent
import com.ysdc.aidpdf.databinding.ActivityPermissionBridgeBinding
import com.ysdc.aidpdf.ui.MainActivity
import com.ysdc.aidpdf.ui.basic.BaseActivity

class OverlayPermissionCheckActivity : BaseActivity<ActivityPermissionBridgeBinding>(
    ActivityPermissionBridgeBinding::inflate
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var settingsDispatched = false
    private val permissionPoll = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (canDrawOverlays()) {
                returnAfterPermissionGranted()
            } else {
                mainHandler.postDelayed(this, POLL_INTERVAL_MILLIS)
            }
        }
    }

    override fun setupViews(savedInstanceState: Bundle?) = Unit

    override fun onResume() {
        super.onResume()
        if (settingsDispatched) {
            finish()
            return
        }

        settingsDispatched = true
        if (launchSystemSettings()) {
            mainHandler.postDelayed(permissionPoll, POLL_INTERVAL_MILLIS)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(permissionPoll)
        (application as? App)?.clearHotStartSkip()
        super.onDestroy()
    }

    private fun launchSystemSettings(): Boolean {
        return runCatching {
            (application as? App)?.skipNextHotStart()
            startActivity(overlaySettingsIntent())
            true
        }.onFailure {
            (application as? App)?.clearHotStartSkip()
            finish()
        }.getOrDefault(false)
    }

    private fun returnToPermissionPage() {
        mainHandler.removeCallbacks(permissionPoll)
        setResult(RESULT_OK)
        runCatching {
            startActivity(Intent(this, OverlayPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }.onFailure {
            mainHandler.postDelayed(permissionPoll, POLL_INTERVAL_MILLIS)
        }
    }

    private fun returnAfterPermissionGranted() {
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_HOME, false)) {
            returnToHome()
        } else {
            returnToPermissionPage()
        }
    }

    private fun returnToHome() {
        mainHandler.removeCallbacks(permissionPoll)
        setResult(RESULT_OK)
        runCatching {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }.onFailure {
            mainHandler.postDelayed(permissionPoll, POLL_INTERVAL_MILLIS)
        }
    }

    companion object {
        const val EXTRA_RETURN_TO_HOME = "extra_return_to_home"
        private const val POLL_INTERVAL_MILLIS = 200L
    }
}

package com.ysdc.aidpdf.ui.permission

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.gate.InterstitialAdGate
import com.ysdc.aidpdf.core.permission.canDrawOverlays
import com.ysdc.aidpdf.databinding.ActivityOverlayPermissionBinding
import com.ysdc.aidpdf.ui.MainActivity
import com.ysdc.aidpdf.ui.basic.BaseActivity
import com.ysdc.aidpdf.ui.guide.OnboardingActivity
import com.ysdc.aidpdf.tracking.AidEventHub
import com.ysdc.aidpdf.tracking.TrackingEventNames

class OverlayPermissionActivity : BaseActivity<ActivityOverlayPermissionBinding>(
    ActivityOverlayPermissionBinding::inflate
) {

    private var navigatingNext = false
    private var firstRunFlow = false
    private var launchFlow = false
    private var permissionReported = false
    private var buttonPulse: ValueAnimator? = null
    private val permissionCheckLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        finishWhenGranted()
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        firstRunFlow = savedInstanceState?.getBoolean(STATE_FIRST_RUN_FLOW)
            ?: intent.getBooleanExtra(EXTRA_FIRST_RUN_FLOW, false)
        launchFlow = savedInstanceState?.getBoolean(STATE_LAUNCH_FLOW)
            ?: intent.getBooleanExtra(EXTRA_LAUNCH_FLOW, false)
        onBackPressedDispatcher.addCallback(this) { }
        AidEventHub.track(TrackingEventNames.FLOATING_NOTIFICATION_POPUP_VIEW)
        InterstitialAdGate.prepare(this, AdScene.TopInterstitial)
        startButtonPulse()
    }

    override fun bindActions() {
        binding.skipButton.setOnClickListener {
            AidEventHub.track(TrackingEventNames.FLOATING_NOTIFICATION_POPUP_SKIP)
            goNextPage()
        }
        binding.allowButton.setOnClickListener {
            AidEventHub.track(TrackingEventNames.FLOATING_NOTIFICATION_POPUP_PASS)
            if (!finishWhenGranted()) {
                permissionCheckLauncher.launch(Intent(this, OverlayPermissionCheckActivity::class.java))
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyEdgeToEdge(binding.root, binding.root)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(EXTRA_FIRST_RUN_FLOW)) {
            firstRunFlow = intent.getBooleanExtra(EXTRA_FIRST_RUN_FLOW, false)
        }
        if (intent.hasExtra(EXTRA_LAUNCH_FLOW)) {
            launchFlow = intent.getBooleanExtra(EXTRA_LAUNCH_FLOW, false)
        }
        setIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_FIRST_RUN_FLOW, firstRunFlow)
        outState.putBoolean(STATE_LAUNCH_FLOW, launchFlow)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        finishWhenGranted()
    }

    override fun onDestroy() {
        buttonPulse?.cancel()
        buttonPulse = null
        super.onDestroy()
    }

    private fun finishWhenGranted(): Boolean {
        if (!canDrawOverlays()) return false
        if (!permissionReported) {
            permissionReported = true
            AidEventHub.track(TrackingEventNames.FLOATING_NOTIFICATION_ALLOW)
        }
        goNextPage()
        return true
    }

    private fun goNextPage() {
        if (navigatingNext || isFinishing || isDestroyed) return
        navigatingNext = true
        InterstitialAdGate.showForClickThenContinue(
            activity = this,
            scene = AdScene.TopInterstitial
        ) {
            openNextPage()
        }
    }

    private fun openNextPage() {
        if (isFinishing || isDestroyed) return
        if (firstRunFlow) {
            startActivity(Intent(this, OnboardingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        } else if (launchFlow) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
        finish()
    }

    private fun startButtonPulse() {
        buttonPulse?.cancel()
        buttonPulse = ValueAnimator.ofFloat(1f, 1.04f).apply {
            duration = 520L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                binding.allowButton.scaleX = scale
                binding.allowButton.scaleY = scale
            }
            start()
        }
    }

    companion object {
        const val EXTRA_FIRST_RUN_FLOW = "extra_first_run_flow"
        const val EXTRA_LAUNCH_FLOW = "extra_launch_flow"
        private const val STATE_FIRST_RUN_FLOW = "state_first_run_flow"
        private const val STATE_LAUNCH_FLOW = "state_launch_flow"
    }
}

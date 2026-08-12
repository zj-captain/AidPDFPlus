package com.ysdc.aidpdf.ui.guide

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.config.AdTrackingScene
import com.ysdc.aidpdf.ad.consent.AidUmpGate
import com.ysdc.aidpdf.ad.gate.InterstitialAdGate
import com.ysdc.aidpdf.ad.gate.NativeAdGate
import com.ysdc.aidpdf.ad.gate.OpenAdGate
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.core.permission.canPostNotifications
import com.ysdc.aidpdf.core.permission.canDrawOverlays
import com.ysdc.aidpdf.databinding.ActivityLaunchLoadingBinding
import com.ysdc.aidpdf.store.isFirstRun
import com.ysdc.aidpdf.reminder.model.EXTRA_REMINDER_TRIGGER
import com.ysdc.aidpdf.reminder.model.ReminderSource
import com.ysdc.aidpdf.reminder.notice.ReminderNotificationCenter
import com.ysdc.aidpdf.reminder.ReminderEventTracker
import com.ysdc.aidpdf.reminder.store.ReminderNavigationStore
import com.ysdc.aidpdf.store.requestSysNotificationCount
import com.ysdc.aidpdf.ui.MainActivity
import com.ysdc.aidpdf.ui.basic.BaseActivity
import com.ysdc.aidpdf.ui.language.LanguageActivity
import com.ysdc.aidpdf.ui.permission.OverlayPermissionActivity
import com.ysdc.aidpdf.ui.permission.OverlayPermissionPromptPolicy
import com.ysdc.aidpdf.ui.uninstall.UninstallProblemActivity
import com.ysdc.aidpdf.tracking.AidEventHub
import com.ysdc.aidpdf.tracking.CoreEventTracker
import com.ysdc.aidpdf.tracking.TrackingEventNames
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class LaunchLoadingActivity :
    BaseActivity<ActivityLaunchLoadingBinding>(ActivityLaunchLoadingBinding::inflate) {

    private var launchJob: Job? = null
    private var launchRequestIndex = 0
    private val launchedForUninstall: Boolean
        get() = intent?.getStringExtra(EXTRA_SHORTCUT_KEY) == SHORTCUT_UNINSTALL
    private val forceOpenAdLaunch: Boolean
        get() = intent?.getBooleanExtra(EXTRA_FORCE_OPEN_AD, false) == true
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) AidEventHub.track(TrackingEventNames.SYSTEM_NOTIFICATION_ALLOW)
        startLaunchFlow()
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        reportLaunchView()
        captureReminderNavigation()
        onBackPressedDispatcher.addCallback(this) {}
        requestNotificationThenStart()
        AdEventTracker.reportChance(launchAdTrackingScene(), launchAdTrackingType() ?: "start")
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyEdgeToEdge(binding.viewRoot, binding.viewRoot)
    }

    private fun startLaunchFlow() {
        launchJob?.cancel()
        launchJob = null
        val requestIndex = ++launchRequestIndex
        /*if (AidUmpGate.canLoadAdsBeforeConsent()) {
            OpenAdGate.prepare()
        }*/
        AidUmpGate.requestBeforeAds(this) {
            Log.e(
                "TAG",
                "startLaunchFlow: requestIndex = $requestIndex  launchRequestIndex = $launchRequestIndex"
            )
            if (requestIndex != launchRequestIndex || isFinishing || isDestroyed) return@requestBeforeAds
            beginOpenAdFlow(requestIndex)
        }
    }

    private fun requestNotificationThenStart() {
        /*if (AidUmpGate.canLoadAdsBeforeConsent()) {
            OpenAdGate.prepare()
        }*/
        if (shouldRequestNotificationPermission()) {
            requestSysNotificationCount++
            AidEventHub.track(TrackingEventNames.SYSTEM_NOTIFICATION_POPUP_VIEW)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startLaunchFlow()
        }
    }

    private fun shouldRequestNotificationPermission(): Boolean {
        return !launchedForUninstall &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !canPostNotifications() &&
                requestSysNotificationCount < 2
    }

    private fun beginOpenAdFlow(requestIndex: Int) {
        launchJob?.cancel()
        launchJob = lifecycleScope.launch {
            val startedAt = System.currentTimeMillis()
            AidAdHub.resetFullScreenInterval()
            OpenAdGate.prepare()
            InterstitialAdGate.prepareHvInterstitial()
            prepareNextPageInventory()
            waitForStartupReady(timeout = 15_000L, interval = 200L, requestIndex)
            keepSplashVisible(startedAt, minimumTime = 1_800L)
            delay(3000L.milliseconds)
            Log.e(
                "TAG",
                "beginOpenAdFlow:requestIndex = $requestIndex   launchRequestIndex = $launchRequestIndex"
            )
            Log.e("TAG", "beginOpenAdFlow: isShowingAd = $isShowingAd  $isLoaded")
            if (requestIndex == launchRequestIndex && isShowingAd && isLoaded) {
                return@launch
            }
            prepareNextPageInventory()
            openNextPage()

            /*OpenAdGate.showThenContinue(
                activity = this@LaunchLoadingActivity,
                trackingScene = launchAdTrackingScene(),
                trackingType = launchAdTrackingType(),
                onShown = {
                    if (requestIndex == launchRequestIndex) {
                        prepareNextPageInventory()
                    }
                }
            ) {
                if (requestIndex != launchRequestIndex) return@showThenContinue
                prepareNextPageInventory()
                openNextPage()
            }*/
        }
    }

    private fun openNextPage() {
        if (isFinishing || isDestroyed) return
        if (launchedForUninstall) {
            startActivity(Intent(this, UninstallProblemActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } else if (isFirstRun) {
            startActivity(LanguageActivity.firstRunIntent(this))
            finish()
        } else if (shouldShowOverlayPermissionPage()) {
            startActivity(Intent(this, OverlayPermissionActivity::class.java).apply {
                putExtra(OverlayPermissionActivity.EXTRA_FIRST_RUN_FLOW, isFirstRun)
                putExtra(OverlayPermissionActivity.EXTRA_LAUNCH_FLOW, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } else {
            openActivity<MainActivity>(finishCurrent = true)
        }
    }

    private fun shouldShowOverlayPermissionPage(): Boolean {
        return OverlayPermissionPromptPolicy.shouldShowLaunchPage(
            source = ReminderEventTracker.source(intent),
            launchedFromAppIcon = launchedFromAppIcon(),
            forceOpenAdLaunch = forceOpenAdLaunch,
            canDrawOverlays = canDrawOverlays(),
            adsBlocked = BlockUtils.shouldBlockAds(this).apply {
                Log.e(
                    "TAG",
                    "shouldShowOverlayPermissionPage: $this"
                )
            }
        )
    }

    private fun launchedFromAppIcon(): Boolean {
        return intent?.action == Intent.ACTION_MAIN &&
                intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true
    }

    private fun launchAdTrackingScene(): String {
        if (launchedForUninstall) return AdTrackingScene.LAUNCH_UNINSTALL
        return when (ReminderEventTracker.source(intent)) {
            ReminderSource.SYSTEM -> AdTrackingScene.LAUNCH_NOTIFICATION
            ReminderSource.FLOATING -> AdTrackingScene.LAUNCH_POPUP
            ReminderSource.MEDIA -> AdTrackingScene.LAUNCH_MEDIA
            ReminderSource.ALWAYS -> AdTrackingScene.LAUNCH_BAR
            null -> AdTrackingScene.LAUNCH_OPEN
        }
    }

    private fun launchAdTrackingType(): String? {
        return when (ReminderEventTracker.source(intent)) {
            ReminderSource.SYSTEM,
            ReminderSource.FLOATING,
            ReminderSource.MEDIA -> ReminderEventTracker.triggerScene(intent)

            ReminderSource.ALWAYS,
            null -> null
        }
    }

    private fun prepareNextPageInventory() {
        //todo
        /*when {
            launchedForUninstall -> {
                InterstitialAdGate.prepare(this, AdScene.UninstallFirstInterstitial)
                InterstitialAdGate.prepare(this, AdScene.UninstallSecondInterstitial)
                InterstitialAdGate.prepareBackMain(this)
                NativeAdGate.prepare(this, AdScene.UninstallFirstNative)
                NativeAdGate.prepare(this, AdScene.UninstallSecondNative)
            }

            else -> {
                InterstitialAdGate.prepareStartupInventory(this)
                NativeAdGate.prepare(this, AdScene.MainNative)
                NativeAdGate.prepare(this, AdScene.ResultNative)
            }
        }*/
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reportLaunchView()
        captureReminderNavigation()
        requestNotificationThenStart()
    }

    private fun reportLaunchView() {
        CoreEventTracker.reportLoadingView()
        CoreEventTracker.reportForegroundSession()
    }

    private fun captureReminderNavigation() {
        ReminderEventTracker.reportClick(intent)
        ReminderNavigationStore.capture(intent)
        if (intent?.hasExtra(EXTRA_REMINDER_TRIGGER) == true) {
            ReminderNotificationCenter.cancelClicked(this, intent)
        }
    }

    override fun onDestroy() {
        launchJob?.cancel()
        launchJob = null
        super.onDestroy()
    }

    private var isShowingAd = false
    private var isLoaded = false
    private suspend fun waitForStartupReady(
        timeout: Long,
        interval: Long,
        requestIndex: Int
    ): Boolean {
        return withTimeoutOrNull(timeout.milliseconds) {
            /*while (!OpenAdGate.ready(this@LaunchLoadingActivity)) {
                delay(interval.milliseconds)
                Log.e("TAG", "waitForStartupReady: 00", )
                OpenAdGate.prepare(this@LaunchLoadingActivity)
            }*/
            while (true) {
                delay(interval.milliseconds)
                if (AidAdHub.fullScreenReady(this@LaunchLoadingActivity, AdScene.HvInterstitial)) {
                    isLoaded = true
                    InterstitialAdGate.showHvInterstitial(
                        activity = this@LaunchLoadingActivity,
                        trackingScene = launchAdTrackingScene(),
                        trackingType = launchAdTrackingType(),
                        onShown = {
                            Log.e("TAG", "waitForStartupReady: showing")
                            isShowingAd = true
                            if (requestIndex == launchRequestIndex) {
                                prepareNextPageInventory()
                            }
                        }
                    ) {
                        if (requestIndex != launchRequestIndex) return@showHvInterstitial
                        prepareNextPageInventory()
                        openNextPage()
                    }
                    break
                } else if (OpenAdGate.ready(this@LaunchLoadingActivity)) {
                    isLoaded = true
                    OpenAdGate.showThenContinue(
                        activity = this@LaunchLoadingActivity,
                        trackingScene = launchAdTrackingScene(),
                        trackingType = launchAdTrackingType(),
                        onShown = {
                            Log.e("TAG", "waitForStartupReady: showing")
                            isShowingAd = true
                            if (requestIndex == launchRequestIndex) {
                                prepareNextPageInventory()
                            }
                        }
                    ) {
                        if (requestIndex != launchRequestIndex) return@showThenContinue
                        prepareNextPageInventory()
                        openNextPage()
                    }
                    break
                }
            }
            true
        } ?: false
    }

    private suspend fun keepSplashVisible(startedAt: Long, minimumTime: Long) {
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed < minimumTime) {
            delay((minimumTime - elapsed).milliseconds)
        }
    }

    companion object {
        const val EXTRA_SHORTCUT_KEY = "key_uninstall"
        const val EXTRA_FORCE_OPEN_AD = "extra_force_open_ad"
        const val SHORTCUT_UNINSTALL = "shortcut_uninstall"
    }
}

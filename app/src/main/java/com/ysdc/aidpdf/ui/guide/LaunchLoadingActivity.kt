package com.ysdc.aidpdf.ui.guide

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ads.Ads
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.consent.AidUmpGate
import com.ysdc.aidpdf.ads.core.AdsEventTracker
import com.ysdc.aidpdf.ads.utils.AdTrackingScene
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.core.permission.canPostNotifications
import com.ysdc.aidpdf.core.permission.canDrawOverlays
import com.ysdc.aidpdf.databinding.ActivityLaunchLoadingBinding
import com.ysdc.aidpdf.reminder.model.EXTRA_REMINDER_TRIGGER
import com.ysdc.aidpdf.reminder.model.ReminderSource
import com.ysdc.aidpdf.reminder.notice.ReminderNotificationCenter
import com.ysdc.aidpdf.reminder.ReminderEventTracker
import com.ysdc.aidpdf.reminder.store.ReminderNavigationStore
import com.ysdc.aidpdf.store.isFirstRun
import com.ysdc.aidpdf.store.requestSysNotificationCount
import com.ysdc.aidpdf.ui.basic.BaseActivity
import com.ysdc.aidpdf.ui.language.LanguageActivity
import com.ysdc.aidpdf.ui.permission.OverlayPermissionPromptPolicy
import com.ysdc.aidpdf.ui.uninstall.UninstallProblemActivity
import com.ysdc.aidpdf.tracking.AidEventHub
import com.ysdc.aidpdf.tracking.CoreEventTracker
import com.ysdc.aidpdf.tracking.TrackingEventNames
import com.ysdc.aidpdf.ui.permission.OverlayPermissionActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.jvm.java
import kotlin.time.Duration.Companion.milliseconds

class LaunchLoadingActivity :
    BaseActivity<ActivityLaunchLoadingBinding>(ActivityLaunchLoadingBinding::inflate) {

    private enum class LaunchAdDecision {
        SHOW_BEST,
        SHOW_SINGLE,
        GO_NEXT
    }

    private data class LaunchAdState(
        val hasTpReady: Boolean,
        val hasAdMobReady: Boolean
    ) {
        val hasAnyReady: Boolean
            get() = hasTpReady || hasAdMobReady

        val hasBothReady: Boolean
            get() = hasTpReady && hasAdMobReady
    }

    private var launchJob: Job? = null
    private var launchRequestIndex = 0
    private var isShowingAd = false
    private var hasHandledOpenAdResult = false
    private var hasNavigatedNextPage = false
    private var hasLoggedSecondStage = false
    private var lastLoggedAdState: LaunchAdState? = null
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
        // 提前预热开屏广告缓存，避免进入 8s/15s 决策时才开始加载。
        Ads.load(AdsScene.Launch, this@LaunchLoadingActivity)
        reportLaunchView()
        captureReminderNavigation()
        onBackPressedDispatcher.addCallback(this) {}
        requestNotificationThenStart()
        AidEventHub.track("Shark_ad_chance",mapOf("scene" to launchAdTrackingScene(),
            "type" to launchAdTrackingType()) )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyEdgeToEdge(binding.viewRoot, binding.viewRoot)
    }

    private fun startLaunchFlow() {
        launchJob?.cancel()
        launchJob = null
        val requestIndex = ++launchRequestIndex
        // 每次重新进入启动流程都必须重置状态，避免旧协程和旧回调污染本次启动。
        isShowingAd = false
        hasHandledOpenAdResult = false
        hasNavigatedNextPage = false
        hasLoggedSecondStage = false
        lastLoggedAdState = null
        if (requestIndex != launchRequestIndex || isFinishing || isDestroyed) return
        // UMP 同意管理预留：当前直接放行，后续接入完整同意流程后再替换占位实现。
        AidUmpGate.requestBeforeAds(this) {
            if (requestIndex != launchRequestIndex || isFinishing || isDestroyed) return@requestBeforeAds
            beginOpenAdFlow(requestIndex)
        }
    }

    private fun requestNotificationThenStart() {
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
            prepareNextPageInventory()
            Log.d(TAG, "启动页广告等待开始：requestIndex=$requestIndex firstStage=${FIRST_STAGE_TIMEOUT_MS}ms total=${TOTAL_TIMEOUT_MS}ms")
            val decision = waitForStartupReady(startedAt, requestIndex)
            keepSplashVisible(startedAt, minimumTime = 1_800L)
            if (requestIndex != launchRequestIndex || isFinishing || isDestroyed) {
                Log.d(TAG, "启动流程已失效，终止后续处理：requestIndex=$requestIndex current=$launchRequestIndex")
                return@launch
            }
            when (decision) {
                LaunchAdDecision.SHOW_BEST,
                LaunchAdDecision.SHOW_SINGLE -> {
                    Log.d(TAG, "启动页命中广告展示条件，准备展示开屏广告：decision=$decision requestIndex=$requestIndex")
                    showLaunchOpenAd(requestIndex)
                }

                LaunchAdDecision.GO_NEXT -> {
                    Log.d(TAG, "启动页在超时内没有可展示广告，直接进入下一页：requestIndex=$requestIndex")
                    prepareNextPageInventory()
                    openNextPage()
                }
            }
        }
    }

    private fun openNextPage() {
        if (hasNavigatedNextPage) {
            Log.d(TAG, "下一页已跳转，忽略重复导航")
            return
        }
        if (isFinishing || isDestroyed) return
        hasNavigatedNextPage = true
        if (launchedForUninstall) {
            startActivity(Intent(this, UninstallProblemActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }else {
            startActivity(LanguageActivity.firstRunIntent(this))
            finish()
        }
    }

    private fun shouldShowOverlayPermissionPage(): Boolean {
        return OverlayPermissionPromptPolicy.shouldShowLaunchPage(
            source = ReminderEventTracker.source(intent),
            launchedFromAppIcon = launchedFromAppIcon(),
            forceOpenAdLaunch = forceOpenAdLaunch,
            canDrawOverlays = canDrawOverlays(),
            adsBlocked = BlockUtils.shouldBlockAds(this).apply {
                Log.e(TAG, "shouldShowOverlayPermissionPage: $this")
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
        // 提前预加载下一页（卸载页/首页）所需广告位。
        Ads.load(AdsScene.BackInterstitial, this)
        Ads.load(AdsScene.ResultInterstitial, this)
        Ads.load(AdsScene.MainNative, this)
        Ads.load(AdsScene.ResultNative, this)
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

    private suspend fun waitForStartupReady(
        startedAt: Long,
        requestIndex: Int
    ): LaunchAdDecision {
        while (true) {
            if (requestIndex != launchRequestIndex || isFinishing || isDestroyed) {
                Log.d(TAG, "启动页等待广告时检测到页面已结束，直接跳过：requestIndex=$requestIndex current=$launchRequestIndex")
                return LaunchAdDecision.GO_NEXT
            }
            val elapsed = System.currentTimeMillis() - startedAt
            val state = currentLaunchAdState()
            logLaunchAdStage(elapsed, state)
            if (state.hasBothReady) {
                Log.d(TAG, "启动页在8秒内双平台都已就绪，直接进入比价展示：elapsed=${elapsed}ms")
                return LaunchAdDecision.SHOW_BEST
            }
            if (elapsed >= FIRST_STAGE_TIMEOUT_MS && state.hasAnyReady) {
                Log.d(
                    TAG,
                    "启动页达到8秒阈值后命中展示条件：elapsed=${elapsed}ms tp=${state.hasTpReady} admob=${state.hasAdMobReady}"
                )
                return if (state.hasBothReady) LaunchAdDecision.SHOW_BEST else LaunchAdDecision.SHOW_SINGLE
            }
            if (elapsed >= TOTAL_TIMEOUT_MS) {
                Log.d(TAG, "启动页达到15秒总超时仍无可展示广告，直接进入下一页")
                return LaunchAdDecision.GO_NEXT
            }
            delay(POLL_INTERVAL_MS.milliseconds)
        }
    }

    private fun currentLaunchAdState(): LaunchAdState {
        return LaunchAdState(
            hasTpReady = Ads.hasReady(AdsScene.Launch, AdsPlatform.TradPlus),
            hasAdMobReady = Ads.hasReady(AdsScene.Launch, AdsPlatform.AdMob)
        )
    }

    private fun logLaunchAdStage(elapsed: Long, state: LaunchAdState) {
        // 只在状态发生变化、阶段切换时打日志，避免轮询日志过多影响排查效率。
        if (lastLoggedAdState != state) {
            val stageLabel = if (elapsed < FIRST_STAGE_TIMEOUT_MS) "0~8s等待阶段" else "8~15s兜底阶段"
            Log.d(
                TAG,
                "启动页广告状态变化：stage=$stageLabel elapsed=${elapsed}ms tp=${state.hasTpReady} admob=${state.hasAdMobReady} any=${state.hasAnyReady} both=${state.hasBothReady}"
            )
            lastLoggedAdState = state
        }
        if (!hasLoggedSecondStage && elapsed >= FIRST_STAGE_TIMEOUT_MS) {
            hasLoggedSecondStage = true
            Log.d(
                TAG,
                "启动页进入8~15秒兜底阶段：elapsed=${elapsed}ms tp=${state.hasTpReady} admob=${state.hasAdMobReady}"
            )
        }
    }

    private fun showLaunchOpenAd(requestIndex: Int) {
        if (hasHandledOpenAdResult || requestIndex != launchRequestIndex || isFinishing || isDestroyed) {
            Log.d(
                TAG,
                "启动页广告展示请求被忽略：handled=$hasHandledOpenAdResult requestIndex=$requestIndex current=$launchRequestIndex finishing=$isFinishing destroyed=$isDestroyed"
            )
            return
        }
        hasHandledOpenAdResult = true
        // 平台选择已下沉到 Ads 模块内部：展示时按 AdMob/TradPlus 价格比较，价高者优先展示。
        Ads.showFullScreen(
            scene = AdsScene.Launch,
            activity = this,
            onShown = {
                isShowingAd = true
                Log.d(TAG, "启动页开屏广告已展示")
                if (requestIndex == launchRequestIndex) {
                    prepareNextPageInventory()
                }
            },
            onClosed = {
                isShowingAd = false
                Log.d(TAG, "启动页开屏广告已关闭，准备进入下一页")
                openNextPage()
            },
            onFailed = {
                isShowingAd = false
                Log.e(TAG, "开屏广告展示失败，message=${it.message}")
                openNextPage()
            },
            trackingScene = launchAdTrackingScene(),
            trackingType = launchAdTrackingType(),
            isOpen = true
        )
    }

    private suspend fun keepSplashVisible(startedAt: Long, minimumTime: Long) {
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed < minimumTime) {
            delay((minimumTime - elapsed).milliseconds)
        }
    }

    companion object {
        private const val TAG = "LaunchLoadingActivity"
        private const val FIRST_STAGE_TIMEOUT_MS = 8_000L
        private const val TOTAL_TIMEOUT_MS = 15_000L
        private const val POLL_INTERVAL_MS = 200L
        const val EXTRA_SHORTCUT_KEY = "key_uninstall"
        const val EXTRA_FORCE_OPEN_AD = "extra_force_open_ad"
        const val SHORTCUT_UNINSTALL = "shortcut_uninstall"
    }
}

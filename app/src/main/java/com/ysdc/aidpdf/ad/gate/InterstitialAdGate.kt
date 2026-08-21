package com.ysdc.aidpdf.ad.gate

import android.content.Context
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.store.appInstance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

object InterstitialAdGate {

    private val showSession = InterstitialAdSession()
    private var lastNavigationShownAt = 0L

    private val startupScenes = listOf(
        AdScene.BottomInterstitial,
        AdScene.TopInterstitial,
        AdScene.CheckInterstitial,
        AdScene.MainBackInterstitial
    )

    fun prepare(
        context: Context = appInstance,
        scene: AdScene = AdScene.BottomInterstitial
    ) {
        if (!scene.isFullScreen || BlockUtils.shouldBlockAds(context)) return
        AidAdHub.loadFullScreen(context, scene)

    }

    fun prepareStartupInventory(context: Context = appInstance) {
        startupScenes.forEach { scene -> prepare(context, scene) }
    }

    fun prepareBackMain(context: Context = appInstance) {
        prepare(context, AdScene.MainBackInterstitial)
    }
    fun resetForAppRestart() {
        showSession.abandon()
        lastNavigationShownAt = 0L
    }

    fun showForBackMainThenContinue(
        activity: AppCompatActivity,
        next: () -> Unit
    ) {
        showForClickThenContinue(
            activity = activity,
            scene = AdScene.MainBackInterstitial,
            trackingScene = AdScene.MainBackInterstitial.trackingKey,
            next = next
        )
    }

    fun showThenContinue(
        activity: AppCompatActivity,
        scene: AdScene = AdScene.BottomInterstitial,
        trackingScene: String = scene.trackingKey,
        onShown: () -> Unit = {},
        next: () -> Unit
    ) {
        if (showSession.isActive || BlockUtils.shouldBlockAds(activity)) {
            next()
            return
        }
        if (!scene.isFullScreen) {
            next()
            return
        }
        AdEventTracker.reportChance(trackingScene)
        val sessionId = showSession.start() ?: run {
            next()
            return
        }
        AidAdHub.showFullScreen(
            activity = activity,
            scene = scene,
            trackingScene = trackingScene,
            onShown = onShown,
            onClosed = {
                finishSession(sessionId, next)
            }
        )
    }
    fun showForClickThenContinue(
        activity: AppCompatActivity,
        scene: AdScene,
        trackingScene: String = scene.trackingKey,
        next: () -> Unit
    ) {
        showCachedOrRequestThenContinue(
            activity = activity,
            scene = scene,
            trackingScene = trackingScene,
            enforceNavigationCooldown = false,
            next = next
        )
    }

    fun showForNavigationThenContinue(
        activity: AppCompatActivity,
        scene: AdScene,
        trackingScene: String = scene.trackingKey,
        next: () -> Unit
    ) {
        showCachedOrRequestThenContinue(
            activity = activity,
            scene = scene,
            trackingScene = trackingScene,
            enforceNavigationCooldown = true,
            next = next
        )
    }

    fun showForProcessingThenContinue(
        activity: AppCompatActivity,
        processingStartedAtMillis: Long,
        scene: AdScene = AdScene.CheckInterstitial,
        trackingScene: String = scene.trackingKey,
        beforeAdOrContinue: () -> Unit = {},
        next: () -> Unit
    ) {
        if (showSession.isActive || !scene.isFullScreen || BlockUtils.shouldBlockAds(activity)) {
            beforeAdOrContinue()
            next()
            return
        }
        AdEventTracker.reportChance(trackingScene)
        val sessionId = showSession.start() ?: run {
            beforeAdOrContinue()
            next()
            return
        }
        var handedOffToAd = false
        val job = activity.lifecycleScope.launch {
            val ready = waitForProcessingAd(activity, scene, processingStartedAtMillis)
            if (!showSession.isActive(sessionId)) return@launch
            if (!ready || !canContinue(activity) || BlockUtils.shouldBlockAds(activity)) {
                finishSession(sessionId) {
                    beforeAdOrContinue()
                    next()
                }
                return@launch
            }
            beforeAdOrContinue()
            handedOffToAd = true
            AidAdHub.showFullScreen(
                activity = activity,
                scene = scene,
                loadingDelayMillis = AD_LOADING_DELAY_MILLIS,
                trackingScene = trackingScene,
                onClosed = {
                    finishSession(sessionId, next)
                }
            )
        }
        job.invokeOnCompletion {
            if (!handedOffToAd) {
                showSession.finish(sessionId)
            }
        }
    }

    fun showForUninstallThenContinue(
        activity: AppCompatActivity,
        scene: AdScene,
        trackingScene: String = scene.trackingKey,
        next: () -> Unit
    ) {
        if (showSession.isActive || !scene.isFullScreen || BlockUtils.shouldBlockAds(activity)) {
            next()
            return
        }
        AdEventTracker.reportChance(trackingScene)
        val sessionId = showSession.start() ?: run {
            next()
            return
        }
        var handedOffToAd = false
        val job = activity.lifecycleScope.launch {
            val ready = if (AidAdHub.fullScreenHasReady(scene)) {
                true
            } else {
                withTimeoutOrNull(UNINSTALL_LOAD_TIMEOUT_MILLIS.milliseconds) {
                    awaitFullScreenReady(activity, scene)
                } == true
            }
            if (!showSession.isActive(sessionId)) return@launch
            if (!ready || !canContinue(activity) || BlockUtils.shouldBlockAds(activity)) {
                finishSession(sessionId) {
                    prepare(activity, scene)
                    next()
                }
                return@launch
            }
            handedOffToAd = true
            AidAdHub.showFullScreen(
                activity = activity,
                scene = scene,
                loadingDelayMillis = AD_LOADING_DELAY_MILLIS,
                trackingScene = trackingScene,
                onClosed = {
                    finishSession(sessionId, next)
                }
            )
        }
        job.invokeOnCompletion {
            if (!handedOffToAd) {
                showSession.finish(sessionId)
            }
        }
    }

    private fun showCachedOrRequestThenContinue(
        activity: AppCompatActivity,
        scene: AdScene,
        trackingScene: String,
        enforceNavigationCooldown: Boolean,
        next: () -> Unit
    ) {
        if (showSession.isActive || !scene.isFullScreen || BlockUtils.shouldBlockAds(activity)) {
            next()
            return
        }
        if (enforceNavigationCooldown && !navigationCooldownReady()) {
            prepare(activity, scene)
            next()
            return
        }
        AdEventTracker.reportChance(trackingScene)
        if (!AidAdHub.fullScreenHasReady(scene)) {
            if (!AidAdHub.fullScreenIsLoading(scene)) {
                prepare(activity, scene)
            }
            next()
            return
        }

        val sessionId = showSession.start() ?: run {
            next()
            return
        }
        AidAdHub.showFullScreen(
            activity = activity,
            scene = scene,
            loadingDelayMillis = AD_LOADING_DELAY_MILLIS,
            trackingScene = trackingScene,
            onShown = {
                if (enforceNavigationCooldown) {
                    lastNavigationShownAt = System.currentTimeMillis()
                }
            },
            onClosed = {
                finishSession(sessionId, next)
            }
        )
    }

    private fun finishSession(sessionId: Long, next: () -> Unit) {
        if (showSession.finish(sessionId)) {
            next()
        }
    }

    private suspend fun waitForProcessingAd(
        activity: AppCompatActivity,
        scene: AdScene,
        startedAtMillis: Long
    ): Boolean {
        if (!AidAdHub.fullScreenHasReady(scene) && !AidAdHub.fullScreenIsLoading(scene)) {
            prepare(activity, scene)
        }

        waitUntilElapsed(startedAtMillis, PROCESSING_MIN_MILLIS)
        if (AidAdHub.fullScreenReady(activity, scene)) return true
        if (!AidAdHub.fullScreenIsLoading(scene)) return false

        val remaining = PROCESSING_MAX_MILLIS - elapsedSince(startedAtMillis)
        if (remaining <= 0L) return AidAdHub.fullScreenReady(activity, scene)
        val loaded = withTimeoutOrNull(remaining) {
            awaitCurrentFullScreenLoad(scene)
        } == true
        return loaded && AidAdHub.fullScreenReady(activity, scene)
    }

    private suspend fun awaitFullScreenReady(context: Context, scene: AdScene): Boolean {
        return suspendCancellableCoroutine { continuation ->
            AidAdHub.waitForFullScreenReady(context, scene) { ready ->
                if (continuation.isActive) {
                    continuation.resume(ready)
                }
            }
        }
    }

    private suspend fun awaitCurrentFullScreenLoad(scene: AdScene): Boolean {
        return suspendCancellableCoroutine { continuation ->
            AidAdHub.waitForCurrentFullScreenLoad(scene) { ready ->
                if (continuation.isActive) {
                    continuation.resume(ready)
                }
            }
        }
    }

    private suspend fun waitUntilElapsed(startedAtMillis: Long, targetMillis: Long) {
        val remaining = targetMillis - elapsedSince(startedAtMillis)
        if (remaining > 0L) {
            delay(remaining)
        }
    }

    private fun elapsedSince(startedAtMillis: Long): Long {
        return System.currentTimeMillis() - startedAtMillis
    }

    private fun navigationCooldownReady(): Boolean {
        if (lastNavigationShownAt <= 0L) return true
        return System.currentTimeMillis() - lastNavigationShownAt >= NAVIGATION_COOLDOWN_MILLIS
    }

    private fun canContinue(activity: AppCompatActivity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        return activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    private const val NAVIGATION_COOLDOWN_MILLIS = 60_000L
    private const val PROCESSING_MIN_MILLIS = 3_000L
    private const val PROCESSING_MAX_MILLIS = 8_000L
    private const val UNINSTALL_LOAD_TIMEOUT_MILLIS = 8_000L
    private const val AD_LOADING_DELAY_MILLIS = 500L
}

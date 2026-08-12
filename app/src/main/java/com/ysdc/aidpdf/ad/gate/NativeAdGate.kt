package com.ysdc.aidpdf.ad.gate

import android.content.Context
import android.os.SystemClock
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.core.AdLease
import com.ysdc.aidpdf.ad.google.NativeAdSize
import com.ysdc.aidpdf.ad.store.NativeAdStore
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.store.appInstance
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.WeakHashMap
import kotlin.coroutines.resume

object NativeAdGate {

    private const val RESUME_POLL_MILLIS = 200L
    private const val NATIVE_REFRESH_DELAY_MILLIS = 1_000L
    private const val VIEW_READY_TIMEOUT_MILLIS = 5_000L
    private val showJobs = WeakHashMap<ViewGroup, Job>()

    fun prepare(
        context: Context = appInstance,
        scene: AdScene = AdScene.MainNative
    ) {
        if (!scene.isNative || BlockUtils.shouldBlockAds(context)) return
        AidAdHub.loadNative(context, scene)
    }

    fun showWhenReady(
        activity: AppCompatActivity,
        parent: ViewGroup,
        scene: AdScene = AdScene.MainNative,
        size: NativeAdSize = NativeAdSize.Tiny,
        trackingScene: String = scene.trackingKey,
        onImpression: () -> Unit = {},
        onShown: (AdLease) -> Unit = {}
    ) {
        if (!scene.isNative || BlockUtils.shouldBlockAds(activity)) {
            cancel(parent)
            parent.removeAllViews()
            return
        }
        AdEventTracker.reportChance(trackingScene)
        val store = AidAdHub.nativeStore(scene) ?: return
        val job = activity.lifecycleScope.launch {
            delay(NATIVE_REFRESH_DELAY_MILLIS)
            if (BlockUtils.shouldBlockAds(activity)) return@launch
            if (!waitUntilVisible(activity, parent)) return@launch
            if (!awaitNativeReady(activity, store)) return@launch
            if (!waitUntilVisible(activity, parent)) return@launch
            if (BlockUtils.shouldBlockAds(activity)) return@launch
            if (!store.canShow(activity)) return@launch
            AidAdHub.showNative(
                activity = activity,
                parent = parent,
                scene = scene,
                size = size,
                trackingScene = trackingScene,
                onImpression = onImpression,
                onShown = onShown
            )
        }
        showJobs[parent]?.cancel()
        showJobs[parent] = job
        job.invokeOnCompletion {
            if (showJobs[parent] === job) {
                showJobs.remove(parent)
            }
        }
    }

    fun cancel(parent: ViewGroup) {
        showJobs.remove(parent)?.cancel()
    }

    private suspend fun awaitNativeReady(context: Context, store: NativeAdStore): Boolean {
        return suspendCancellableCoroutine { continuation ->
            store.waitUntilReady(context) { ready ->
                if (continuation.isActive) {
                    continuation.resume(ready)
                }
            }
        }
    }

    private suspend fun waitUntilVisible(activity: AppCompatActivity, parent: ViewGroup): Boolean {
        val startedAt = System.currentTimeMillis()
        while (!canContinue(activity, parent)) {
            if (activity.isFinishing || activity.isDestroyed) return false
            if (System.currentTimeMillis() - startedAt >= VIEW_READY_TIMEOUT_MILLIS) return false
            delay(RESUME_POLL_MILLIS)
        }
        return true
    }

    private fun canContinue(activity: AppCompatActivity, parent: ViewGroup): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        return parent.isAttachedToWindow
    }
}

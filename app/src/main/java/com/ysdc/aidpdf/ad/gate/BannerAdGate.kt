package com.ysdc.aidpdf.ad.gate

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.core.block.BlockUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BannerAdGate {

    private var bannerJob: Job? = null

    fun showWhenResumed(
        activity: AppCompatActivity,
        parent: ViewGroup,
        scene: AdScene = AdScene.MainBanner,
        shouldShow: () -> Boolean = { true },
        onChance: () -> Unit = {},
        onImpression: () -> Unit = {}
    ) {
        bannerJob?.cancel()
        if (BlockUtils.shouldBlockAds(activity)) {
            destroy(parent, scene)
            return
        }
        bannerJob = activity.lifecycleScope.launch {
            delay(BANNER_LOAD_DELAY_MILLIS)
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            if (!parent.isAttachedToWindow) return@launch
            if (shouldShow().not()) return@launch
            if (BlockUtils.shouldBlockAds(activity)) return@launch
            onChance()
            AdEventTracker.reportChance(scene.trackingKey)
            AidAdHub.showBanner(activity, parent, scene, onImpression)
        }
    }

    fun destroy(parent: ViewGroup? = null, scene: AdScene = AdScene.MainBanner) {
        bannerJob?.cancel()
        bannerJob = null
        AidAdHub.destroyBanner(parent, scene)
    }

    private companion object {
        private const val BANNER_LOAD_DELAY_MILLIS = 1_000L
    }
}

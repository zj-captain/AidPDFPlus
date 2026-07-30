package com.ysdc.aidpdf.ad

import android.app.Application
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.MobileAds
import com.ysdc.aidpdf.BuildConfig
import com.ysdc.aidpdf.ad.config.AdCatalog
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.core.AdLease
import com.ysdc.aidpdf.ad.google.NativeAdSize
import com.ysdc.aidpdf.ad.store.BannerAdStore
import com.ysdc.aidpdf.ad.store.FullScreenAdStore
import com.ysdc.aidpdf.ad.store.NativeAdStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AidAdHub {

    private const val TAG = "AidAdHub"
    private const val FULL_SCREEN_COOLDOWN_MILLIS = 0L

    private val fullScreenStores: Map<AdScene, FullScreenAdStore> by lazy {
        AdScene.fullScreenScenes.associateWith { scene -> FullScreenAdStore(scene) }
    }
    private val nativeStores: Map<AdScene, NativeAdStore> by lazy {
        AdScene.nativeScenes.associateWith { scene -> NativeAdStore(scene) }
    }
    private val bannerStores: Map<AdScene, BannerAdStore> by lazy {
        AdScene.bannerScenes.associateWith { scene -> BannerAdStore(scene) }
    }

    private var initialized = false
    private var lastFullScreenClosedAt = 0L

    fun initialize(application: Application) {
        if (initialized) return
        initialized = true
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            runCatching { MobileAds.initialize(application) }
                .onFailure { log("AdMob init skipped: ${it.message}") }
        }
    }

    fun configure(catalog: AdCatalog) {
        AdScene.fullScreenScenes.forEach { scene ->
            fullScreenStores[scene]?.configure(catalog.unitsFor(scene))
        }
        AdScene.nativeScenes.forEach { scene ->
            nativeStores[scene]?.configure(catalog.unitsFor(scene))
        }
        AdScene.bannerScenes.forEach { scene ->
            bannerStores[scene]?.configure(catalog.unitsFor(scene))
        }
    }

    fun loadFullScreen(context: Context, scene: AdScene) {
        if (!scene.isFullScreen) return
        val store = fullScreenStores[scene] ?: return
        if (!store.hasReady() && !store.isLoading()) {
            store.load(context)
        }
    }

    fun fullScreenHasReady(scene: AdScene): Boolean {
        if (!scene.isFullScreen) return false
        return fullScreenStores[scene]?.hasReady() == true
    }

    fun fullScreenIsLoading(scene: AdScene): Boolean {
        if (!scene.isFullScreen) return false
        return fullScreenStores[scene]?.isLoading() == true
    }

    fun fullScreenReady(activity: AppCompatActivity, scene: AdScene): Boolean {
        if (!scene.isFullScreen) return false
        return fullScreenStores[scene]?.canShow(activity) == true
    }

    fun waitForFullScreenReady(
        context: Context,
        scene: AdScene,
        callback: (Boolean) -> Unit
    ) {
        if (!scene.isFullScreen) {
            callback(false)
            return
        }
        fullScreenStores[scene]?.waitUntilReady(context, callback) ?: callback(false)
    }

    fun waitForCurrentFullScreenLoad(
        scene: AdScene,
        callback: (Boolean) -> Unit
    ) {
        if (!scene.isFullScreen) {
            callback(false)
            return
        }
        fullScreenStores[scene]?.waitCurrentLoad(callback) ?: callback(false)
    }

    fun showFullScreen(
        activity: AppCompatActivity,
        scene: AdScene,
        loadingDelayMillis: Long = 500L,
        trackingScene: String = scene.trackingKey,
        trackingType: String? = null,
        onShown: () -> Unit = {},
        onClosed: () -> Unit
    ) {
        if (!scene.isFullScreen) {
            onClosed()
            return
        }
        val store = fullScreenStores[scene]
        if (store == null) {
            onClosed()
            return
        }
        store.show(
            activity = activity,
            loadingDelayMillis = loadingDelayMillis,
            sceneOverride = trackingScene,
            trackingType = trackingType,
            onShown = onShown,
            onClosed = onClosed
        )
    }

    fun loadNative(context: Context, scene: AdScene) {
        if (!scene.isNative) return
        val store = nativeStores[scene] ?: return
        if (!store.hasReady() && !store.isLoading()) {
            store.load(context)
        }
    }

    fun nativeStore(scene: AdScene): NativeAdStore? {
        return nativeStores[scene]
    }

    fun showNative(
        activity: AppCompatActivity,
        parent: ViewGroup,
        scene: AdScene,
        size: NativeAdSize = NativeAdSize.Tiny,
        trackingScene: String = scene.trackingKey,
        onImpression: () -> Unit = {},
        onShown: (AdLease) -> Unit = {}
    ) {
        if (!scene.isNative) return
        nativeStores[scene]?.show(
            activity = activity,
            parent = parent,
            size = size,
            sceneOverride = trackingScene,
            onImpression = onImpression,
            onShown = onShown
        )
    }

    fun showBanner(
        activity: AppCompatActivity,
        parent: ViewGroup,
        scene: AdScene = AdScene.MainBanner,
        onImpression: () -> Unit = {}
    ) {
        if (!scene.isBanner) return
        bannerStores[scene]?.show(activity, parent, onImpression)
    }

    fun destroyBanner(parent: ViewGroup? = null, scene: AdScene = AdScene.MainBanner) {
        bannerStores[scene]?.destroy(parent)
    }

    fun fullScreenIntervalReady(isOpenAd: Boolean): Boolean {
        if (isOpenAd) return true
        if (lastFullScreenClosedAt <= 0L) return true
        return System.currentTimeMillis() - lastFullScreenClosedAt >= FULL_SCREEN_COOLDOWN_MILLIS
    }

    fun markFullScreenClosed() {
        lastFullScreenClosedAt = System.currentTimeMillis()
    }

    fun resetFullScreenInterval() {
        lastFullScreenClosedAt = 0L
    }

    fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}

package com.ysdc.aidpdf.ads

import android.app.Activity
import android.app.Application
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.ysdc.aidpdf.ads.config.AdsCatalog
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.core.AdsExceptionInfo
import com.ysdc.aidpdf.ads.core.AdsLogger
import com.ysdc.aidpdf.ads.init.AdsInitializer
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.BannerRenderRequest
import com.ysdc.aidpdf.ads.model.NativeRenderRequest
import com.ysdc.aidpdf.ads.repository.BannerCoordinator
import com.ysdc.aidpdf.ads.repository.CachedAdsRepository

object Ads {
    private var cachedRepository: CachedAdsRepository? = null
    private var bannerCoordinator: BannerCoordinator? = null

    fun initialize(application: Application) {
        AdsInitializer.initialize(application)
        cachedRepository = CachedAdsRepository(application.applicationContext)
        bannerCoordinator = BannerCoordinator()
    }

    fun configure(catalog: AdsCatalog) {
        val cached = cachedRepository
        val banner = bannerCoordinator
        if (cached == null || banner == null) {
            AdsLogger.w("广告模块尚未初始化，忽略配置")
            return
        }
        cached.configure(catalog)
        banner.configure(catalog)
    }

    fun load(scene: AdsScene, activity: Activity? = null) {
        load(scene, AdsPlatform.AdMob, activity)
        load(scene, AdsPlatform.TradPlus, activity)
    }

    fun load(scene: AdsScene, platform: AdsPlatform, activity: Activity? = null) {
        cachedRepository?.load(scene, platform, activity)
    }

    fun hasReady(scene: AdsScene, platform: AdsPlatform): Boolean {
        return cachedRepository?.hasReady(scene, platform) == true
    }

    fun showFullScreen(
        scene: AdsScene,
        platform: AdsPlatform,
        activity: AppCompatActivity,
        onShown: () -> Unit = {},
        onClosed: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit = {}
    ) {
        cachedRepository?.showFullScreen(scene, platform, activity, onShown, onClosed, onFailed)
    }

    /**
     * 全屏广告展示入口：业务层不再指定平台，由模块内部按 AdMob/TradPlus 展示前价格比较，价高者优先。
     */
    fun showFullScreen(
        scene: AdsScene,
        activity: AppCompatActivity,
        onShown: () -> Unit = {},
        onClosed: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit = {}
    ) {
        cachedRepository?.showBestFullScreen(scene, activity, onShown, onClosed, onFailed)
    }

    fun showNative(
        scene: AdsScene,
        platform: AdsPlatform,
        activity: AppCompatActivity,
        parent: ViewGroup,
        request: NativeRenderRequest,
        onShown: () -> Unit = {},
        onImpression: () -> Unit = {},
        onFailed: (AdsExceptionInfo) -> Unit = {}
    ): AdDisplayHandle? {
        return cachedRepository?.showNative(
            scene,
            platform,
            activity,
            parent,
            request,
            onShown,
            onImpression,
            onFailed
        )
    }

    /**
     * 原生广告展示入口：业务层不再指定平台，由模块内部按 AdMob/TradPlus 展示前价格比较，价高者优先。
     */
    fun showNative(
        scene: AdsScene,
        activity: AppCompatActivity,
        parent: ViewGroup,
        request: NativeRenderRequest,
        onShown: () -> Unit = {},
        onImpression: () -> Unit = {},
        onFailed: (AdsExceptionInfo) -> Unit = {}
    ): AdDisplayHandle? {
        return cachedRepository?.showBestNative(
            scene,
            activity,
            parent,
            request,
            onShown,
            onImpression,
            onFailed
        )
    }

    fun showBanner(
        scene: AdsScene,
        platform: AdsPlatform,
        activity: AppCompatActivity,
        parent: ViewGroup,
        request: BannerRenderRequest = BannerRenderRequest(),
        onImpression: () -> Unit = {},
        onFailed: (AdsExceptionInfo) -> Unit = {}
    ): AdDisplayHandle? {
        return bannerCoordinator?.showBanner(
            scene,
            platform,
            activity,
            parent,
            request,
            onImpression,
            onFailed
        )
    }

    fun invalidate(scene: AdsScene, platform: AdsPlatform, activity: Activity? = null) {
        cachedRepository?.invalidate(scene, platform, activity)
    }

    fun destroyScene(scene: AdsScene, platform: AdsPlatform) {
        cachedRepository?.destroyScene(scene, platform)
    }

    fun destroyAll() {
        cachedRepository?.destroyAll()
        bannerCoordinator?.destroyAll()
    }
}

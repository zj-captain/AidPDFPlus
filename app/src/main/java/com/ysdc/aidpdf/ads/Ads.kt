package com.ysdc.aidpdf.ads

import android.app.Activity
import android.app.Application
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.ysdc.aidpdf.ad.AdsAdmobLimitManager
import com.ysdc.aidpdf.ads.config.AdsCatalog
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.core.AdsErrorCode
import com.ysdc.aidpdf.ads.core.AdsExceptionInfo
import com.ysdc.aidpdf.ads.core.AdsLogger
import com.ysdc.aidpdf.ads.init.AdsInitializer
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.BannerRenderRequest
import com.ysdc.aidpdf.ads.model.NativeRenderRequest
import com.ysdc.aidpdf.ads.repository.BannerCoordinator
import com.ysdc.aidpdf.ads.repository.CachedAdsRepository
import com.ysdc.aidpdf.ad.AdsLimitManager
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.tracking.AidEventHub

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
        onFailed: (AdsExceptionInfo) -> Unit = {},
        trackingScene: String? = null,
        trackingType: String? = null,
        isOpen: Boolean? = false,
    ) {
        // 每日广告展示上限检查
        if (!AdsLimitManager.canShow()) {
            AdsLogger.d("广告展示被每日上限拦截: scene=$scene platform=$platform")
            onFailed(
                AdsExceptionInfo(
                    code = AdsErrorCode.DailyLimitExceeded,
                    scene = scene,
                    platform = platform,
                    message = "今日广告展示已达上限"
                )
            )
            return
        }
        if (isOpen == false) {
            AidEventHub.track("Shark_ad_chance", mapOf("scene" to trackingScene))
        }
        cachedRepository?.showFullScreen(
            scene, platform, activity,
            onShown = { AdsLimitManager.recordShow(); onShown() },
            onClosed, onFailed, trackingScene, trackingType
        )
    }

    /**
     * 全屏广告自动入口：不再做自动竞价。
     * 规则：AdMob 未达到专属上限时优先展示 AdMob，失败后 TradPlus 兜底；
     * AdMob 达到专属上限后，直接展示 TradPlus。
     */
    fun showFullScreen(
        scene: AdsScene,
        activity: AppCompatActivity,
        onShown: () -> Unit = {},
        onClosed: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit = {},
        trackingScene: String? = null,
        trackingType: String? = null,
        isOpen: Boolean? = false,
    ) {
        // 每日广告展示上限检查
        if (!AdsLimitManager.canShow()) {
            AdsLogger.d("广告展示被每日上限拦截: scene=$scene (自动竞价)")
            onFailed(
                AdsExceptionInfo(
                    code = AdsErrorCode.DailyLimitExceeded,
                    scene = scene,
                    platform = AdsPlatform.TradPlus,
                    message = "今日广告展示已达上限"
                )
            )
            return
        }
        val preferAdMob = AdsAdmobLimitManager.canShow()
        val primaryPlatform = if (preferAdMob) AdsPlatform.AdMob else AdsPlatform.TradPlus
        val fallbackPlatform = if (preferAdMob) AdsPlatform.TradPlus else null
        AdsLogger.d("广告展示优先级：scene=$scene 首选平台=$primaryPlatform 兜底平台=${fallbackPlatform ?: "无"}")
        showFullScreen(
            scene = scene,
            platform = primaryPlatform,
            activity = activity,
            onShown = {
                if (primaryPlatform == AdsPlatform.AdMob) {
                    // AdMob 专属上限只在自动入口命中 AdMob 成功展示时累计。
                    AdsAdmobLimitManager.recordShow()
                }
                onShown()
            },
            onClosed = onClosed,
            onFailed = { error ->
                val nextPlatform = fallbackPlatform
                if (nextPlatform == null) {
                    onFailed(error)
                    return@showFullScreen
                }
                AdsLogger.w("广告展示首选平台失败，切换兜底平台：scene=$scene from=$primaryPlatform to=$nextPlatform reason=${error.message}")
                showFullScreen(
                    scene = scene,
                    platform = nextPlatform,
                    activity = activity,
                    onShown = onShown,
                    onClosed = onClosed,
                    onFailed = onFailed,
                    trackingScene = trackingScene,
                    trackingType = trackingType
                )
            },
            trackingScene = trackingScene,
            trackingType = trackingType
        )
    }

    fun showNative(
        scene: AdsScene,
        platform: AdsPlatform,
        activity: AppCompatActivity,
        parent: ViewGroup,
        request: NativeRenderRequest,
        onShown: () -> Unit = {},
        onImpression: () -> Unit = {},
        onFailed: (AdsExceptionInfo) -> Unit = {},
        trackingScene: String? = null
    ): AdDisplayHandle? {
        // 每日广告展示上限检查
        if (!AdsLimitManager.canShow()) {
            AdsLogger.d("广告展示被每日上限拦截: scene=$scene 平台=$platform (原生)")
            onFailed(
                AdsExceptionInfo(
                    code = AdsErrorCode.DailyLimitExceeded,
                    scene = scene,
                    platform = platform,
                    message = "今日广告展示已达上限"
                )
            )
            return null
        }
        return cachedRepository?.showNative(
            scene, platform, activity, parent, request,
            onShown = { AdsLimitManager.recordShow(); onShown() },
            onImpression, onFailed, trackingScene
        )
    }

    /**
     * 原生广告自动入口：不再做自动竞价。
     * 规则：AdMob 未达到专属上限时优先展示 AdMob，拿不到有效 handle 时 TradPlus 兜底；
     * AdMob 达到专属上限后，直接展示 TradPlus。
     */
    fun showNative(
        scene: AdsScene,
        activity: AppCompatActivity,
        parent: ViewGroup,
        request: NativeRenderRequest,
        onShown: () -> Unit = {},
        onImpression: () -> Unit = {},
        onFailed: (AdsExceptionInfo) -> Unit = {},
        trackingScene: String? = null
    ): AdDisplayHandle? {
        if (BlockUtils.shouldBlockAds(activity)) {
            onFailed(
                AdsExceptionInfo(
                    code = AdsErrorCode.DailyLimitExceeded,
                    scene = scene,
                    platform = AdsPlatform.TradPlus,
                    message = "广告被拦截屏蔽"
                )
            )
            return null
        }
        // 每日广告展示上限检查
        if (!AdsLimitManager.canShow()) {
            AdsLogger.d("广告展示被每日上限拦截: scene=$scene (自动竞价原生)")
            onFailed(
                AdsExceptionInfo(
                    code = AdsErrorCode.DailyLimitExceeded,
                    scene = scene,
                    platform = AdsPlatform.TradPlus,
                    message = "今日广告展示已达上限"
                )
            )
            return null
        }
        AidEventHub.track("Shark_ad_chance", mapOf("scene" to trackingScene))
        val preferAdMob = AdsAdmobLimitManager.canShow()
        val primaryPlatform = if (preferAdMob) AdsPlatform.AdMob else AdsPlatform.TradPlus
        val fallbackPlatform = if (preferAdMob) AdsPlatform.TradPlus else null
        AdsLogger.d("广告展示优先级：scene=$scene 原生首选平台=$primaryPlatform 兜底平台=${fallbackPlatform ?: "无"}")
        val primaryHandle = showNative(
            scene = scene,
            platform = primaryPlatform,
            activity = activity,
            parent = parent,
            request = request,
            onShown = {
                if (primaryPlatform == AdsPlatform.AdMob) {
                    // AdMob 专属上限只在自动入口命中 AdMob 成功展示时累计。
                    AdsAdmobLimitManager.recordShow()
                }
                onShown()
            },
            onImpression = onImpression,
            onFailed = { error ->
                // 原生展示是否兜底，取决于首选平台是否已经返回有效 handle；
                // 这里仅处理首选平台在 showNative 内同步失败的情况。
                if (fallbackPlatform == null) {
                    onFailed(error)
                }
            },
            trackingScene = trackingScene
        )
        if (primaryHandle != null || fallbackPlatform == null) {
            return primaryHandle
        }
        AdsLogger.w("广告展示首选平台未返回有效 handle，切换兜底平台：scene=$scene from=$primaryPlatform to=$fallbackPlatform")
        return showNative(
            scene = scene,
            platform = fallbackPlatform,
            activity = activity,
            parent = parent,
            request = request,
            onShown = onShown,
            onImpression = onImpression,
            onFailed = onFailed,
            trackingScene = trackingScene
        )
    }

    fun showBanner(
        scene: AdsScene,
        platform: AdsPlatform,
        activity: AppCompatActivity,
        parent: ViewGroup,
        request: BannerRenderRequest = BannerRenderRequest(),
        onImpression: () -> Unit = {},
        onFailed: (AdsExceptionInfo) -> Unit = {},
        trackingScene: String? = null
    ): AdDisplayHandle? {
        // 每日广告展示上限检查
        if (!AdsLimitManager.canShow()) {
            AdsLogger.d("广告展示被每日上限拦截: scene=$scene 平台=$platform (Banner)")
            onFailed(
                AdsExceptionInfo(
                    code = AdsErrorCode.DailyLimitExceeded,
                    scene = scene,
                    platform = platform,
                    message = "今日广告展示已达上限"
                )
            )
            return null
        }
        AidEventHub.track("Shark_ad_chance", mapOf("scene" to trackingScene))
        // Banner 展示是同步的，返回非 null 即展示成功，直接记录
        val handle = bannerCoordinator?.showBanner(
            scene, platform, activity, parent, request, onImpression, onFailed, trackingScene
        )
        if (handle != null) {
            AdsLimitManager.recordShow()
        }
        return handle
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

package com.ysdc.aidpdf.ads.repository

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.ysdc.aidpdf.ads.config.AdsCatalog
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.core.AdsErrorCode
import com.ysdc.aidpdf.ads.core.AdsExceptionInfo
import com.ysdc.aidpdf.ads.core.AdsLogger
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.BannerRenderRequest
import com.ysdc.aidpdf.ads.provider.ProviderFactory
import com.ysdc.aidpdf.ads.runtime.ActiveDisplayRegistry

class BannerCoordinator(
    private val displayRegistry: ActiveDisplayRegistry = ActiveDisplayRegistry()
) {
    private var catalog: AdsCatalog = AdsCatalog()

    fun configure(newCatalog: AdsCatalog) {
        catalog = newCatalog
    }

    fun showBanner(
        scene: AdsScene,
        platform: AdsPlatform,
        activity: AppCompatActivity,
        parent: ViewGroup,
        request: BannerRenderRequest,
        onImpression: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit
    ): AdDisplayHandle? {
        if (displayRegistry.hasBanner(parent)) {
            val error = AdsExceptionInfo(
                code = AdsErrorCode.ParentAlreadyOccupied,
                scene = scene,
                platform = platform,
                message = "当前 parent 已存在未释放的 banner handle，请先 destroy 再 show"
            )
            AdsLogger.w(error.message)
            onFailed(error)
            return null
        }
        val candidates = catalog.unitsFor(scene, platform)
        if (candidates.isEmpty()) {
            onFailed(
                AdsExceptionInfo(
                    code = AdsErrorCode.PlatformNotConfigured,
                    scene = scene,
                    platform = platform,
                    message = "当前平台没有可用的 Banner 配置"
                )
            )
            return null
        }
        candidates.forEach { config ->
            val handle = ProviderFactory.banner(platform).showBanner(
                config = config,
                activity = activity,
                parent = parent,
                request = request,
                onImpression = onImpression,
                onFailed = onFailed
            ) ?: return@forEach
            val wrappedHandle = object : AdDisplayHandle {
                private var destroyed = false

                override fun destroy() {
                    if (destroyed) return
                    destroyed = true
                    handle.destroy()
                    displayRegistry.unregisterBanner(parent, this)
                }
            }
            displayRegistry.registerBanner(parent, wrappedHandle)
            return wrappedHandle
        }
        AdsLogger.w("场景=$scene 平台=$platform Banner 本轮候选位全部展示失败")
        return null
    }

    fun destroyAll() {
        displayRegistry.snapshotHandles().forEach { it.destroy() }
        displayRegistry.clear()
    }
}

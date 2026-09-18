package com.ysdc.aidpdf.ads.provider.tradplus

import android.app.Activity
import android.content.Context
import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.tradplus.ads.base.bean.TPAdError
import com.tradplus.ads.base.bean.TPAdInfo
import com.tradplus.ads.base.bean.TPBaseAd
import com.tradplus.ads.open.interstitial.InterstitialAdListener
import com.tradplus.ads.open.interstitial.TPInterstitial
import com.tradplus.ads.open.nativead.NativeAdListener
import com.tradplus.ads.open.nativead.TPNative
import com.tradplus.ads.open.splash.SplashAdListener
import com.tradplus.ads.open.splash.TPSplash
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ads.config.AdsFormat
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsUnitConfig
import com.ysdc.aidpdf.ads.core.AdsLogger
import com.ysdc.aidpdf.ads.core.AdsErrorCode
import com.ysdc.aidpdf.ads.core.AdsEventTracker
import com.ysdc.aidpdf.ads.core.AdsExceptionInfo
import com.ysdc.aidpdf.ads.core.AdsThread
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.NativeAdStyle
import com.ysdc.aidpdf.ads.model.NativeRenderRequest
import com.ysdc.aidpdf.ads.provider.CachedAdProvider
import com.ysdc.aidpdf.reminder.model.ReminderTrigger
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter.trigger

class TradPlusCachedAdProvider : CachedAdProvider {
    override fun supports(platform: AdsPlatform, format: AdsFormat): Boolean {
        return platform == AdsPlatform.TradPlus
    }

    override fun load(
        config: AdsUnitConfig,
        appContext: Context,
        activity: Activity?,
        callback: (Result<Any>) -> Unit
    ) {
        val hostActivity = activity ?: run {
            callback(Result.failure(IllegalStateException("TradPlus 加载必须提供 Activity")))
            return
        }
        when (config.format) {
            AdsFormat.Open -> loadSplash(hostActivity, config, callback)
            AdsFormat.Interstitial -> loadInterstitial(hostActivity, config, callback)
            AdsFormat.Native -> loadNative(hostActivity, config, callback)
            AdsFormat.Banner -> callback(Result.failure(IllegalStateException("Banner 不走缓存 provider")))
        }
    }

    private fun loadSplash(
        activity: Activity,
        config: AdsUnitConfig,
        callback: (Result<Any>) -> Unit
    ) {
        // TradPlus 按广告位长期复用对象，避免每次加载重复创建底层实例。
        val splash = TradPlusHolderRegistry.getOrCreateSplash(activity, config.unitId)
        splash.setAdListener(object : SplashAdListener() {
            override fun onAdClicked(tpAdInfo: TPAdInfo?) = Unit

            override fun onAdImpression(tpAdInfo: TPAdInfo?) = Unit

            override fun onAdClosed(tpAdInfo: TPAdInfo?) = Unit

            override fun onAdLoaded(tpAdInfo: TPAdInfo?, tpBaseAd: TPBaseAd?) {
                AdsLogger.d("TradPlus 开屏缓存加载成功，创建独立 TPSplash：unitId=${config.unitId}")
                callback(Result.success(TradPlusOpenPayload(config, splash)))
            }

            override fun onAdLoadFailed(tpAdError: TPAdError?) {
                AdsLogger.w("TradPlus 开屏缓存加载失败：unitId=${config.unitId} error=${tpAdError?.errorMsg}")
                callback(Result.failure(IllegalStateException(tpAdError?.errorMsg)))
            }
        })
        splash.loadAd(null)
    }

    private fun loadInterstitial(
        activity: Activity,
        config: AdsUnitConfig,
        callback: (Result<Any>) -> Unit
    ) {
        // TradPlus 按广告位长期复用对象，避免每次加载重复创建底层实例。
        val interstitial = TradPlusHolderRegistry.getOrCreateInterstitial(activity, config.unitId)
        interstitial.setAdListener(object : InterstitialAdListener {
            override fun onAdLoaded(tpAdInfo: TPAdInfo?) {
                AdsLogger.d("TradPlus 插屏缓存加载成功，创建独立 TPInterstitial：unitId=${config.unitId}")
                callback(Result.success(TradPlusInterstitialPayload(config, interstitial)))
            }

            override fun onAdClicked(tpAdInfo: TPAdInfo?) = Unit

            override fun onAdImpression(tpAdInfo: TPAdInfo?) = Unit

            override fun onAdFailed(tpAdError: TPAdError?) {
                AdsLogger.w("TradPlus 插屏缓存加载失败：unitId=${config.unitId} error=${tpAdError?.errorMsg}")
                callback(Result.failure(IllegalStateException(tpAdError?.errorMsg)))
            }

            override fun onAdClosed(tpAdInfo: TPAdInfo?) = Unit

            override fun onAdVideoError(tpAdInfo: TPAdInfo?, tpAdError: TPAdError?) = Unit

            override fun onAdVideoStart(tpAdInfo: TPAdInfo?) = Unit

            override fun onAdVideoEnd(tpAdInfo: TPAdInfo?) = Unit
        })
        interstitial.loadAd()
    }

    private fun loadNative(
        activity: Activity,
        config: AdsUnitConfig,
        callback: (Result<Any>) -> Unit
    ) {
        // TradPlus 按广告位长期复用对象，避免每次加载重复创建底层实例。
        val nativeAd = TradPlusHolderRegistry.getOrCreateNative(activity, config.unitId)
        nativeAd.setAdListener(object : NativeAdListener() {
            override fun onAdLoaded(tpAdInfo: TPAdInfo?, tpBaseAd: TPBaseAd?) {
                AdsLogger.d("TradPlus 原生缓存加载成功，创建独立 TPNative：unitId=${config.unitId}")
                callback(Result.success(TradPlusNativePayload(config, nativeAd)))
            }

            override fun onAdClicked(tpAdInfo: TPAdInfo?) = Unit

            override fun onAdImpression(tpAdInfo: TPAdInfo?) = Unit

            override fun onAdShowFailed(tpAdError: TPAdError?, tpAdInfo: TPAdInfo?) = Unit

            override fun onAdLoadFailed(tpAdError: TPAdError?) {
                AdsLogger.w("TradPlus 原生缓存加载失败：unitId=${config.unitId} error=${tpAdError?.errorMsg}")
                callback(Result.failure(IllegalStateException(tpAdError?.errorMsg)))
            }

            override fun onAdClosed(tpAdInfo: TPAdInfo?) = Unit
        })
        nativeAd.loadAd()
    }

    override fun showFullScreen(
        payload: Any,
        activity: AppCompatActivity,
        onShown: () -> Unit,
        onClosed: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit,
        trackingScene: String?,
        trackingType: String?,
        ecpm: Double?
    ) {
        when (payload) {
            is TradPlusOpenPayload -> {
                if (activity.isFinishing || activity.isDestroyed) {
                    val error = AdsExceptionInfo(
                        code = AdsErrorCode.ShowFailed,
                        scene = payload.config.scene,
                        platform = AdsPlatform.TradPlus,
                        message = "TradPlus 开屏展示失败：Activity 已结束",
                        unitId = payload.config.unitId
                    )
                    AdsLogger.w("TradPlus 开屏展示被忽略：Activity 已结束 unitId=${payload.config.unitId}")
                    AdsEventTracker.reportShowFailed(payload.config, trackingScene, error)
                    onFailed(error)
                    return
                }
                val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
                if (contentRoot == null) {
                    val error = AdsExceptionInfo(
                        code = AdsErrorCode.ShowFailed,
                        scene = payload.config.scene,
                        platform = AdsPlatform.TradPlus,
                        message = "TradPlus 开屏展示失败：未找到 Activity content 容器",
                        unitId = payload.config.unitId
                    )
                    AdsLogger.e("TradPlus 开屏展示失败：未找到 Activity content 容器 unitId=${payload.config.unitId}")
                    AdsEventTracker.reportShowFailed(payload.config, trackingScene, error)
                    onFailed(error)
                    return
                }
                // TradPlus 官方文档要求 showAd(ViewGroup adContainer) 传入容器；
                // 这里必须挂到真实视图树，避免使用未 attach 的临时 View 导致不展示也不回调。
                val splashContainer = FrameLayout(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                contentRoot.addView(splashContainer)
                AdsLogger.d("TradPlus 开屏容器已挂载到 contentRoot，准备展示：unitId=${payload.config.unitId} childCount=${contentRoot.childCount}")
                fun removeSplashContainer(reason: String) {
                    val parent = splashContainer.parent
                    if (parent is ViewGroup) {
                        parent.removeView(splashContainer)
                        AdsLogger.d("TradPlus 开屏容器已移除：unitId=${payload.config.unitId} reason=$reason")
                    } else {
                        AdsLogger.d("TradPlus 开屏容器移除跳过：unitId=${payload.config.unitId} reason=$reason parent=$parent")
                    }
                }
                payload.ad.setAdListener(object : SplashAdListener() {
                    override fun onAdClicked(tpAdInfo: TPAdInfo?) {
                        AdsThread.runOnMain {
                            trigger(ReminderTrigger.AD_CLICK)
                            AdsEventTracker.reportClick(payload.config, trackingScene) }
                    }
                    override fun onAdImpression(tpAdInfo: TPAdInfo?) {
                        AdsThread.runOnMain {
                            // 补传最终展示价（TPAdInfo.ecpm 为美元 eCPM），供埋点计算 gap。
                            AdsEventTracker.reportShown(payload.config, trackingScene, trackingType, ecpm, tpAdInfo?.ecpm?.toDoubleOrNull(), source = tpAdInfo?.adSourceName)
                            AdEventTracker.sendTpRevenue(tpAdInfo?.ecpm?.toDoubleOrNull()?:0.00,"USD",tpAdInfo?.adSourceName)
                            AdEventTracker.reportTotalAdsRenenue001Tp(tpAdInfo?.ecpm?.toDoubleOrNull()?:0.00)
                            onShown()
                        }
                    }

                    override fun onAdClosed(tpAdInfo: TPAdInfo?) {
                        AdsThread.runOnMain {
                            removeSplashContainer("onAdClosed")
                            AdsEventTracker.reportClosed(payload.config, trackingScene)
                            onClosed()
                        }
                    }

                    override fun onAdLoaded(tpAdInfo: TPAdInfo?, tpBaseAd: TPBaseAd?) = Unit

                    override fun onAdLoadFailed(tpAdError: TPAdError?) {
                        AdsThread.runOnMain {
                            removeSplashContainer("onAdLoadFailed")
                            val error = AdsExceptionInfo(
                                code = AdsErrorCode.ShowFailed,
                                scene = payload.config.scene,
                                platform = AdsPlatform.TradPlus,
                                message = tpAdError?.errorMsg ?: "TradPlus splash show failed",
                                unitId = payload.config.unitId
                            )
                            AdsEventTracker.reportShowFailed(payload.config, trackingScene, error)
                            onFailed(error)
                        }
                    }

                    override fun onAdShowFailed(tpAdInfo: TPAdInfo?, tpAdError: TPAdError?) {
                        AdsThread.runOnMain {
                            removeSplashContainer("onAdShowFailed")
                            val error = AdsExceptionInfo(
                                code = AdsErrorCode.ShowFailed,
                                scene = payload.config.scene,
                                platform = AdsPlatform.TradPlus,
                                message = tpAdError?.errorMsg ?: "TradPlus splash show failed",
                                unitId = payload.config.unitId
                            )
                            AdsLogger.e("TradPlus 开屏展示失败回调：unitId=${payload.config.unitId} error=${tpAdError?.errorMsg}")
                            AdsEventTracker.reportShowFailed(payload.config, trackingScene, error)
                            onFailed(error)
                        }
                    }
                })
                AdsLogger.d("TradPlus 开始调用开屏 showAd：unitId=${payload.config.unitId}")
                payload.ad.showAd(splashContainer)
            }

            is TradPlusInterstitialPayload -> {
                if (!payload.ad.isReady) {
                    val error = AdsExceptionInfo(
                        code = AdsErrorCode.ShowFailed,
                        scene = payload.config.scene,
                        platform = AdsPlatform.TradPlus,
                        message = "TradPlus 插屏当前不可展示",
                        unitId = payload.config.unitId
                    )
                    AdsEventTracker.reportShowFailed(payload.config, trackingScene, error)
                    onFailed(error)
                    return
                }
                payload.ad.setAdListener(object : InterstitialAdListener {
                    override fun onAdLoaded(tpAdInfo: TPAdInfo?) = Unit

                    override fun onAdClicked(tpAdInfo: TPAdInfo?) {
                        AdsThread.runOnMain {
                            trigger(ReminderTrigger.AD_CLICK)
                            AdsEventTracker.reportClick(payload.config, trackingScene) }
                    }

                    override fun onAdImpression(tpAdInfo: TPAdInfo?) {
                        AdsThread.runOnMain {
                            // 补传最终展示价（TPAdInfo.ecpm 为美元 eCPM），供埋点计算 gap。
                            AdsEventTracker.reportShown(payload.config, trackingScene, trackingType, ecpm, tpAdInfo?.ecpm?.toDoubleOrNull(), source = tpAdInfo?.adSourceName)
                            AdEventTracker.sendTpRevenue(tpAdInfo?.ecpm?.toDoubleOrNull()?:0.00,"USD",tpAdInfo?.adSourceName)
                            AdEventTracker.reportTotalAdsRenenue001Tp(tpAdInfo?.ecpm?.toDoubleOrNull()?:0.00)
                            onShown()
                        }
                    }

                    override fun onAdFailed(tpAdError: TPAdError?) {
                        AdsThread.runOnMain {
                            val error = AdsExceptionInfo(
                                code = AdsErrorCode.ShowFailed,
                                scene = payload.config.scene,
                                platform = AdsPlatform.TradPlus,
                                message = tpAdError?.errorMsg ?: "TradPlus interstitial show failed",
                                unitId = payload.config.unitId
                            )
                            AdsEventTracker.reportShowFailed(payload.config, trackingScene, error)
                            onFailed(error)
                        }
                    }

                    override fun onAdClosed(tpAdInfo: TPAdInfo?) {
                        AdsThread.runOnMain {
                            AdsEventTracker.reportClosed(payload.config, trackingScene)
                            onClosed()
                        }
                    }

                    override fun onAdVideoError(tpAdInfo: TPAdInfo?, tpAdError: TPAdError?) = Unit

                    override fun onAdVideoStart(tpAdInfo: TPAdInfo?) = Unit

                    override fun onAdVideoEnd(tpAdInfo: TPAdInfo?) = Unit
                })
                payload.ad.showAd(activity, null)
            }
        }
    }

    override fun showNative(
        payload: Any,
        activity: AppCompatActivity,
        parent: ViewGroup,
        request: NativeRenderRequest,
        onShown: () -> Unit,
        onImpression: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit,
        trackingScene: String?,
        ecpm: Double?
    ): AdDisplayHandle? {
        payload as? TradPlusNativePayload ?: return null
        if (!payload.ad.isReady) {
            val error = AdsExceptionInfo(
                code = AdsErrorCode.ShowFailed,
                scene = payload.config.scene,
                platform = AdsPlatform.TradPlus,
                message = "TradPlus 原生当前不可展示",
                unitId = payload.config.unitId
            )
            AdsEventTracker.reportShowFailed(payload.config, trackingScene, error)
            onFailed(error)
            return null
        }
        // TradPlus 原生模板不能复用 AdMob 的 NativeAdView/MediaView 根布局，
        // 否则 SDK 可能无法正确绑定素材，最终只剩空白占位。
        val layoutId = when (request.style) {
            NativeAdStyle.Large -> R.layout.layout_ad_native_tradplus_large
            NativeAdStyle.Medium -> R.layout.layout_ad_native_tradplus_medium
            NativeAdStyle.Tiny -> R.layout.layout_ad_native_tradplus_tiny
        }
        payload.ad.setAdListener(object : NativeAdListener() {
            override fun onAdLoaded(tpAdInfo: TPAdInfo?, tpBaseAd: TPBaseAd?) = Unit

            override fun onAdClicked(tpAdInfo: TPAdInfo?) {
                AdsThread.runOnMain {
                    trigger(ReminderTrigger.AD_CLICK)
                    AdsEventTracker.reportClick(payload.config, trackingScene) }
            }

            override fun onAdImpression(tpAdInfo: TPAdInfo?) {
                AdsThread.runOnMain {
                    // 补传最终展示价（TPAdInfo.ecpm 为美元 eCPM），供埋点计算 gap。
                    AdsEventTracker.reportShown(payload.config, trackingScene, ecpm = ecpm, reEcpm = tpAdInfo?.ecpm?.toDoubleOrNull(), source = tpAdInfo?.adSourceName)
                    AdEventTracker.sendTpRevenue(tpAdInfo?.ecpm?.toDoubleOrNull()?:0.00,"USD",tpAdInfo?.adSourceName)
                    AdEventTracker.reportTotalAdsRenenue001Tp(tpAdInfo?.ecpm?.toDoubleOrNull()?:0.00)
                    onImpression()
                }
            }

            override fun onAdShowFailed(tpAdError: TPAdError?, tpAdInfo: TPAdInfo?) {
                AdsThread.runOnMain {
                    val error = AdsExceptionInfo(
                        code = AdsErrorCode.ShowFailed,
                        scene = payload.config.scene,
                        platform = AdsPlatform.TradPlus,
                        message = tpAdError?.errorMsg ?: "TradPlus native show failed",
                        unitId = payload.config.unitId
                    )
                    AdsEventTracker.reportShowFailed(payload.config, trackingScene, error)
                    onFailed(error)
                }
            }

            override fun onAdLoadFailed(tpAdError: TPAdError?) = Unit

            override fun onAdClosed(tpAdInfo: TPAdInfo?) = Unit
        })
        payload.ad.showAd(parent, layoutId)
        onShown()
        return TradPlusDisplayHandle(parent = parent, child = parent, onDestroyAction = { payload.ad.onDestroy() })
    }

    override fun destroyPayload(payload: Any) {
        when (payload) {
            is TradPlusOpenPayload -> {
                // TradPlus 现在采用广告位级对象复用，缓存销毁时不能顺手销毁 holder，
                // 否则会把同广告位后续继续复用的对象一并释放。
                AdsLogger.d("TradPlus 开屏缓存移除：unitId=${payload.config.unitId}，保留 TPSplash holder 供后续复用")
            }
            is TradPlusInterstitialPayload -> {
                AdsLogger.d("TradPlus 插屏缓存移除：unitId=${payload.config.unitId}，保留 TPInterstitial holder 供后续复用")
            }
            is TradPlusNativePayload -> {
                AdsLogger.d("TradPlus 原生缓存移除：unitId=${payload.config.unitId}，保留 TPNative holder 供后续复用")
            }
        }
    }
}

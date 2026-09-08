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
import com.ysdc.aidpdf.ads.config.AdsFormat
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsUnitConfig
import com.ysdc.aidpdf.ads.core.AdsErrorCode
import com.ysdc.aidpdf.ads.core.AdsExceptionInfo
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.NativeAdStyle
import com.ysdc.aidpdf.ads.model.NativeRenderRequest
import com.ysdc.aidpdf.ads.provider.CachedAdProvider

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
        val splash = TradPlusHolderRegistry.getOrCreateSplash(activity, config.unitId)
        splash.setAdListener(object : SplashAdListener() {
            override fun onAdClicked(tpAdInfo: TPAdInfo) = Unit

            override fun onAdImpression(tpAdInfo: TPAdInfo) = Unit

            override fun onAdClosed(tpAdInfo: TPAdInfo) = Unit

            override fun onAdLoaded(tpAdInfo: TPAdInfo, tpBaseAd: TPBaseAd) {
                callback(Result.success(TradPlusOpenPayload(config, splash)))
            }

            override fun onAdLoadFailed(tpAdError: TPAdError) {
                callback(Result.failure(IllegalStateException(tpAdError.errorMsg)))
            }
        })
        splash.loadAd(null)
    }

    private fun loadInterstitial(
        activity: Activity,
        config: AdsUnitConfig,
        callback: (Result<Any>) -> Unit
    ) {
        val interstitial = TradPlusHolderRegistry.getOrCreateInterstitial(activity, config.unitId)
        interstitial.setAdListener(object : InterstitialAdListener {
            override fun onAdLoaded(tpAdInfo: TPAdInfo) {
                callback(Result.success(TradPlusInterstitialPayload(config, interstitial)))
            }

            override fun onAdClicked(tpAdInfo: TPAdInfo) = Unit

            override fun onAdImpression(tpAdInfo: TPAdInfo) = Unit

            override fun onAdFailed(tpAdError: TPAdError) {
                callback(Result.failure(IllegalStateException(tpAdError.errorMsg)))
            }

            override fun onAdClosed(tpAdInfo: TPAdInfo) = Unit

            override fun onAdVideoError(tpAdInfo: TPAdInfo, tpAdError: TPAdError) = Unit

            override fun onAdVideoStart(tpAdInfo: TPAdInfo) = Unit

            override fun onAdVideoEnd(tpAdInfo: TPAdInfo) = Unit
        })
        interstitial.loadAd()
    }

    private fun loadNative(
        activity: Activity,
        config: AdsUnitConfig,
        callback: (Result<Any>) -> Unit
    ) {
        val nativeAd = TradPlusHolderRegistry.getOrCreateNative(activity, config.unitId)
        nativeAd.setAdListener(object : NativeAdListener() {
            override fun onAdLoaded(tpAdInfo: TPAdInfo, tpBaseAd: TPBaseAd) {
                callback(Result.success(TradPlusNativePayload(config, nativeAd)))
            }

            override fun onAdClicked(tpAdInfo: TPAdInfo) = Unit

            override fun onAdImpression(tpAdInfo: TPAdInfo) = Unit

            override fun onAdShowFailed(tpAdError: TPAdError, tpAdInfo: TPAdInfo) = Unit

            override fun onAdLoadFailed(tpAdError: TPAdError) {
                callback(Result.failure(IllegalStateException(tpAdError.errorMsg)))
            }

            override fun onAdClosed(tpAdInfo: TPAdInfo) = Unit
        })
        nativeAd.loadAd()
    }

    override fun showFullScreen(
        payload: Any,
        activity: AppCompatActivity,
        onShown: () -> Unit,
        onClosed: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit
    ) {
        when (payload) {
            is TradPlusOpenPayload -> {
                payload.ad.setAdListener(object : SplashAdListener() {
                    override fun onAdClicked(tpAdInfo: TPAdInfo) = Unit

                    override fun onAdImpression(tpAdInfo: TPAdInfo) {
                        onShown()
                    }

                    override fun onAdClosed(tpAdInfo: TPAdInfo) {
                        onClosed()
                    }

                    override fun onAdLoaded(tpAdInfo: TPAdInfo, tpBaseAd: TPBaseAd) = Unit

                    override fun onAdLoadFailed(tpAdError: TPAdError) {
                        onFailed(
                            AdsExceptionInfo(
                                code = AdsErrorCode.ShowFailed,
                                scene = payload.config.scene,
                                platform = AdsPlatform.TradPlus,
                                message = tpAdError.errorMsg,
                                unitId = payload.config.unitId
                            )
                        )
                    }
                })
                val container = FrameLayout(activity)
                payload.ad.showAd(container)
            }

            is TradPlusInterstitialPayload -> {
                if (!payload.ad.isReady) {
                    onFailed(
                        AdsExceptionInfo(
                            code = AdsErrorCode.ShowFailed,
                            scene = payload.config.scene,
                            platform = AdsPlatform.TradPlus,
                            message = "TradPlus 插屏当前不可展示",
                            unitId = payload.config.unitId
                        )
                    )
                    return
                }
                payload.ad.setAdListener(object : InterstitialAdListener {
                    override fun onAdLoaded(tpAdInfo: TPAdInfo) = Unit

                    override fun onAdClicked(tpAdInfo: TPAdInfo) = Unit

                    override fun onAdImpression(tpAdInfo: TPAdInfo) {
                        onShown()
                    }

                    override fun onAdFailed(tpAdError: TPAdError) {
                        onFailed(
                            AdsExceptionInfo(
                                code = AdsErrorCode.ShowFailed,
                                scene = payload.config.scene,
                                platform = AdsPlatform.TradPlus,
                                message = tpAdError.errorMsg,
                                unitId = payload.config.unitId
                            )
                        )
                    }

                    override fun onAdClosed(tpAdInfo: TPAdInfo) {
                        onClosed()
                    }

                    override fun onAdVideoError(tpAdInfo: TPAdInfo, tpAdError: TPAdError) = Unit

                    override fun onAdVideoStart(tpAdInfo: TPAdInfo) = Unit

                    override fun onAdVideoEnd(tpAdInfo: TPAdInfo) = Unit
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
        onFailed: (AdsExceptionInfo) -> Unit
    ): AdDisplayHandle? {
        payload as? TradPlusNativePayload ?: return null
        if (!payload.ad.isReady) {
            onFailed(
                AdsExceptionInfo(
                    code = AdsErrorCode.ShowFailed,
                    scene = payload.config.scene,
                    platform = AdsPlatform.TradPlus,
                    message = "TradPlus 原生当前不可展示",
                    unitId = payload.config.unitId
                )
            )
            return null
        }
        val layoutId = when (request.style) {
            NativeAdStyle.Large -> R.layout.layout_ad_native_large
            NativeAdStyle.Medium -> R.layout.layout_ad_native_medium
            NativeAdStyle.Tiny -> R.layout.layout_ad_native_tiny
        }
        payload.ad.setAdListener(object : NativeAdListener() {
            override fun onAdLoaded(tpAdInfo: TPAdInfo, tpBaseAd: TPBaseAd) = Unit

            override fun onAdClicked(tpAdInfo: TPAdInfo) = Unit

            override fun onAdImpression(tpAdInfo: TPAdInfo) {
                onImpression()
            }

            override fun onAdShowFailed(tpAdError: TPAdError, tpAdInfo: TPAdInfo) {
                onFailed(
                    AdsExceptionInfo(
                        code = AdsErrorCode.ShowFailed,
                        scene = payload.config.scene,
                        platform = AdsPlatform.TradPlus,
                        message = tpAdError.errorMsg,
                        unitId = payload.config.unitId
                    )
                )
            }

            override fun onAdLoadFailed(tpAdError: TPAdError) = Unit

            override fun onAdClosed(tpAdInfo: TPAdInfo) = Unit
        })
        payload.ad.showAd(parent, layoutId)
        onShown()
        return TradPlusDisplayHandle(parent = parent, child = parent, onDestroyAction = { payload.ad.onDestroy() })
    }

    override fun destroyPayload(payload: Any) {
        when (payload) {
            is TradPlusOpenPayload -> TradPlusHolderRegistry.destroy(payload.config.unitId, AdsFormat.Open)
            is TradPlusInterstitialPayload -> TradPlusHolderRegistry.destroy(payload.config.unitId, AdsFormat.Interstitial)
            is TradPlusNativePayload -> TradPlusHolderRegistry.destroy(payload.config.unitId, AdsFormat.Native)
        }
    }
}

package com.ysdc.aidpdf.ads.provider.admob

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ads.config.AdsFormat
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.config.AdsUnitConfig
import com.ysdc.aidpdf.ads.core.AdsErrorCode
import com.ysdc.aidpdf.ads.core.AdsEventTracker
import com.ysdc.aidpdf.ads.core.AdsExceptionInfo
import com.ysdc.aidpdf.ads.core.AdsThread
import com.ysdc.aidpdf.ads.provider.CachedAdProvider
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.NativeRenderRequest

class AdMobCachedAdProvider : CachedAdProvider {
    override fun supports(platform: AdsPlatform, format: AdsFormat): Boolean {
        return platform == AdsPlatform.AdMob
    }

    override fun load(
        config: AdsUnitConfig,
        appContext: Context,
        activity: Activity?,
        callback: (Result<Any>) -> Unit
    ) {
        when (config.format) {
            AdsFormat.Open -> {
                AppOpenAd.load(
                    AdRequest.Builder(config.unitId).build(),
                    object : AdLoadCallback<AppOpenAd> {
                        override fun onAdLoaded(ad: AppOpenAd) {
                            callback(Result.success(AdMobOpenPayload(config, ad)))
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            callback(Result.failure(IllegalStateException(loadAdError.message)))
                        }
                    }
                )
            }

            AdsFormat.Interstitial -> {
                InterstitialAd.load(
                    AdRequest.Builder(config.unitId).build(),
                    object : AdLoadCallback<InterstitialAd> {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            callback(Result.success(AdMobInterstitialPayload(config, ad)))
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            callback(Result.failure(IllegalStateException(loadAdError.message)))
                        }
                    }
                )
            }

            AdsFormat.Native -> {
                val adRequest = NativeAdRequest.Builder(config.unitId, listOf(NativeAd.NativeAdType.NATIVE)).build()
                NativeAdLoader.load(adRequest, object : NativeAdLoaderCallback {
                    override fun onNativeAdLoaded(nativeAd: NativeAd) {
                        callback(Result.success(AdMobNativePayload(config, nativeAd)))
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        callback(Result.failure(IllegalStateException(loadAdError.message)))
                    }
                })
            }

            AdsFormat.Banner -> callback(Result.failure(IllegalStateException("Banner 不走缓存 provider")))
        }
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
            is AdMobOpenPayload -> {
                payload.ad.adEventCallback = object : AppOpenAdEventCallback {
                    // AdMob 事件回调可能被 SDK 调度到后台线程（如 GMA BG），必须切回主线程再通知业务层。
                    override fun onAdShowedFullScreenContent() {
                        AdsThread.runOnMain {
                            onShown()
                        }
                    }

                    override fun onAdPaid(value: AdValue) {
                        AdsThread.runOnMain {
                            AdsEventTracker.reportShown(payload.config, trackingScene, trackingType, ecpm,value.valueMicros / 1_000_000.0 * 1000)
                            AdEventTracker.reportPaidValue(trackingScene?:payload.config.scene.remoteKey, payload.config, value, payload.ad.getResponseInfo())
                            AdEventTracker.reportTotalAdsRenenue001Admob(value)
                        }
                    }
                    override fun onAdClicked() {
                        AdsThread.runOnMain {
                            AdsEventTracker.reportClick(payload.config, trackingScene)
                        }
                    }

                    override fun onAdDismissedFullScreenContent() {
                        AdsThread.runOnMain {
                            AdsEventTracker.reportClosed(payload.config, trackingScene)
                            onClosed()
                        }
                    }

                    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                        AdsThread.runOnMain {
                            val error = AdsExceptionInfo(
                                code = AdsErrorCode.ShowFailed,
                                scene = AdsScene.Launch,
                                platform = AdsPlatform.AdMob,
                                message = fullScreenContentError.message,
                                unitId = payload.config.unitId
                            )
                            AdsEventTracker.reportShowFailed(payload.config, trackingScene, error)
                            onFailed(error)
                        }
                    }
                }
                payload.ad.show(activity)
            }

            is AdMobInterstitialPayload -> {
                payload.ad.adEventCallback = object : InterstitialAdEventCallback {
                    // AdMob 事件回调可能被 SDK 调度到后台线程（如 GMA BG），必须切回主线程再通知业务层。
                    override fun onAdShowedFullScreenContent() {
                        AdsThread.runOnMain {
                            onShown()
                        }
                    }

                    override fun onAdPaid(value: AdValue) {
                        AdsThread.runOnMain {
                            AdsEventTracker.reportShown(payload.config, trackingScene, trackingType, ecpm,value.valueMicros / 1_000_000.0 * 1000)
                            AdEventTracker.reportPaidValue(trackingScene?:payload.config.scene.remoteKey, payload.config, value, payload.ad.getResponseInfo())
                            AdEventTracker.reportTotalAdsRenenue001Admob(value)
                        }
                    }
                    override fun onAdClicked() {
                        AdsThread.runOnMain {
                            AdsEventTracker.reportClick(payload.config, trackingScene)
                        }
                    }

                    override fun onAdDismissedFullScreenContent() {
                        AdsThread.runOnMain {
                            AdsEventTracker.reportClosed(payload.config, trackingScene)
                            onClosed()
                        }
                    }

                    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                        AdsThread.runOnMain {
                            val error = AdsExceptionInfo(
                                code = AdsErrorCode.ShowFailed,
                                scene = payload.config.scene,
                                platform = AdsPlatform.AdMob,
                                message = fullScreenContentError.message,
                                unitId = payload.config.unitId
                            )
                            AdsEventTracker.reportShowFailed(payload.config, trackingScene, error)
                            onFailed(error)
                        }
                    }
                }
                payload.ad.show(activity)
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
        payload as? AdMobNativePayload ?: return null
        // 展示埋点迁到 onAdPaid：拿到最终展示价后再上报，才能计算 gap。
        // 回调注册放在渲染/registerNativeAd 之前，避免错过 SDK 早期回调（对齐全屏“先设回调再 show”）。
        payload.ad.adEventCallback = object : NativeAdEventCallback {
            override fun onAdPaid(value: AdValue) {
                AdsThread.runOnMain {
                    AdsEventTracker.reportShown(
                        payload.config,
                        trackingScene,
                        ecpm = ecpm,
                        reEcpm = value.valueMicros / 1_000_000.0 * 1000
                    )
                    AdEventTracker.reportPaidValue(trackingScene?:payload.config.scene.remoteKey, payload.config, value, payload.ad.getResponseInfo())
                    AdEventTracker.reportTotalAdsRenenue001Admob(value)
                }
            }
        }
        val nativeAdView: NativeAdView = AdMobNativeRenderer.createAndBind(parent, payload, request.style)
        parent.addView(nativeAdView)
        onShown()
        onImpression()
        return AdMobDisplayHandle(parent = parent, child = nativeAdView, nativeAd = payload.ad)
    }

    override fun destroyPayload(payload: Any) {
        when (payload) {
            is AdMobNativePayload -> payload.ad.destroy()
        }
    }
}

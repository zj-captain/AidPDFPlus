package com.ysdc.aidpdf.ads.provider.admob

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.ysdc.aidpdf.ads.config.AdsFormat
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.config.AdsUnitConfig
import com.ysdc.aidpdf.ads.core.AdsErrorCode
import com.ysdc.aidpdf.ads.core.AdsExceptionInfo
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
        onFailed: (AdsExceptionInfo) -> Unit
    ) {
        when (payload) {
            is AdMobOpenPayload -> {
                payload.ad.adEventCallback = object : AppOpenAdEventCallback {
                    override fun onAdShowedFullScreenContent() {
                        onShown()
                    }

                    override fun onAdDismissedFullScreenContent() {
                        onClosed()
                    }

                    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                        onFailed(
                            AdsExceptionInfo(
                                code = AdsErrorCode.ShowFailed,
                                scene = AdsScene.Launch,
                                platform = AdsPlatform.AdMob,
                                message = fullScreenContentError.message,
                                unitId = payload.config.unitId
                            )
                        )
                    }
                }
                payload.ad.show(activity)
            }

            is AdMobInterstitialPayload -> {
                payload.ad.adEventCallback = object : InterstitialAdEventCallback {
                    override fun onAdShowedFullScreenContent() {
                        onShown()
                    }

                    override fun onAdDismissedFullScreenContent() {
                        onClosed()
                    }

                    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                        onFailed(
                            AdsExceptionInfo(
                                code = AdsErrorCode.ShowFailed,
                                scene = payload.config.scene,
                                platform = AdsPlatform.AdMob,
                                message = fullScreenContentError.message,
                                unitId = payload.config.unitId
                            )
                        )
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
        onFailed: (AdsExceptionInfo) -> Unit
    ): AdDisplayHandle? {
        payload as? AdMobNativePayload ?: return null
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

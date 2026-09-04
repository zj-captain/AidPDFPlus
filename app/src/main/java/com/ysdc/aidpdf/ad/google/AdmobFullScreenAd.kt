package com.ysdc.aidpdf.ad.google

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdFormat
import com.ysdc.aidpdf.ad.config.AdUnitConfig
import com.ysdc.aidpdf.ad.core.AdLoadResult
import com.ysdc.aidpdf.ad.core.AdRenderRequest
import com.ysdc.aidpdf.ad.core.CachedAd
import com.ysdc.aidpdf.ad.remote.AdRemoteBridge
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
import com.ysdc.aidpdf.tracking.AidEventHub.reportAdClick
import com.ysdc.aidpdf.tracking.AidEventHub.reportAdClose
import com.ysdc.aidpdf.tracking.AidEventHub.reportAdLoaded
import com.ysdc.aidpdf.tracking.AidEventHub.reportAdShow
import com.ysdc.aidpdf.tracking.AidEventHub.reportAdShowFailed
import com.ysdc.aidpdf.tracking.AidEventHub.reportStartLoading
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class AdmobFullScreenAd(
    override var sceneName: String,
    override val config: AdUnitConfig,
    override val requestId: String = UUID.randomUUID().toString().replace("-", ""),
    override var loadedAtMillis: Long = System.currentTimeMillis()
) : CachedAd {
    private val request = AdRequest.Builder().build()
    private var sdkAd: Any? = null
    private var displayed = false
    private var trackingType: String? = null

    override fun load(context: Context, callback: (AdLoadResult) -> Unit) {
//        AdEventTracker.reportLoadStarted(sceneName)
        when (config.format) {
            AdFormat.Open -> {
                reportStartLoading(5, "splash", sceneName, config.unitId, requestId)
                loadAppOpen(context, callback)
            }

            AdFormat.Interstitial -> {
                reportStartLoading(
                    3,
                    "interstitial",
                    sceneName,
                    config.unitId,
                    requestId
                )
                loadInterstitial(context, callback)
            }

            else -> {
                val reason = "Unsupported full-screen format."
                AdEventTracker.reportLoadFailed(sceneName, INTERNAL_ERROR_CODE, reason)
                callback(AdLoadResult.Failed(reason))
            }
        }
    }

    override fun present(request: AdRenderRequest) {
        displayed = false
        val callback = fullScreenCallback(
            activity = request.activity,
            trackingType = request.trackingType,
            onPresented = request.onPresented,
            onFinished = request.onFinished,
            sdkAd is AppOpenAd
        )
        when (val ad = sdkAd) {
            is AppOpenAd -> {
                ad.fullScreenContentCallback = callback
                if (AdRemoteBridge.virtual_block_switch == 1) {
                    ad.setImmersiveMode(true)
                } else {
                    ad.setImmersiveMode(false)
                }
                ad.show(request.activity)
            }

            is InterstitialAd -> {
                ad.fullScreenContentCallback = callback
                if (AdRemoteBridge.virtual_block_switch == 1) {
                    ad.setImmersiveMode(true)
                } else {
                    ad.setImmersiveMode(false)
                }
                ad.show(request.activity)
            }

            else -> {
                AdEventTracker.reportImpressionFailed(
                    sceneName,
                    INTERNAL_ERROR_CODE,
                    "Ad is unavailable."
                )
                continueWhenResumed(request.activity, request.onFinished)
            }
        }
    }

    override fun release() {
        sdkAd = null
    }

    private fun loadAppOpen(context: Context, callback: (AdLoadResult) -> Unit) {
        AidAdHub.log("$sceneName app-open loading id=$requestId")
        AppOpenAd.load(context, config.unitId, request, object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                ad.setOnPaidEventListener { value ->
                    AidAdHub.log("$sceneName paid value=${value.valueMicros} currency=${value.currencyCode}")
                    reportAdShow(
                        5, "splash", sceneName,
                        ad.adUnitId, value.valueMicros / 1_000_000.0 * 1000,
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceName ?: "Admob",
                        200, "", requestId, trackingType
                    )
                    AdEventTracker.reportPaidValue(sceneName, config, value, ad.responseInfo)
                    AdEventTracker.reportTotalAdsRenenue001(value)
                }
                sdkAd = ad
                loadedAtMillis = System.currentTimeMillis()
                reportAdLoaded(
                    5, "splash", sceneName,
                    config.unitId, 0.0,
                    ad.responseInfo.loadedAdapterResponseInfo?.adSourceName ?: "Admob",
                    200, "", requestId
                )
//                AdEventTracker.reportLoadSucceeded(sceneName)
                callback(AdLoadResult.Loaded)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                reportAdLoaded(
                    5,
                    "splash",
                    sceneName,
                    config.unitId,
                    0.0,
                    "Admob",
                    error.code,
                    error.message,
                    requestId
                )
//                AdEventTracker.reportLoadFailed(sceneName, error.code, error.message)
                callback(AdLoadResult.Failed(error.message,error.code))
//                callback(AdLoadResult.Failed("No fill.",3))
            }
        })
    }

    private fun loadInterstitial(context: Context, callback: (AdLoadResult) -> Unit) {
        AidAdHub.log("$sceneName interstitial loading id=$requestId")
        InterstitialAd.load(context, config.unitId, request, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                ad.setOnPaidEventListener { value ->
                    AidAdHub.log("$sceneName paid value=${value.valueMicros} currency=${value.currencyCode}")
                    reportAdShow(
                        3, "interstitial", sceneName,
                        ad.adUnitId, value.valueMicros / 1_000_000.0 * 1000,
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceName ?: "Admob",
                        200, "", requestId
                    )
                    AdEventTracker.reportPaidValue(sceneName, config, value, ad.responseInfo)
                    AdEventTracker.reportTotalAdsRenenue001(value)
                }
                sdkAd = ad
                loadedAtMillis = System.currentTimeMillis()
                reportAdLoaded(
                    3, "interstitial", sceneName,
                    config.unitId, 0.0,
                    ad.responseInfo.loadedAdapterResponseInfo?.adSourceName ?: "Admob",
                    200, "", requestId
                )
//                AdEventTracker.reportLoadSucceeded(sceneName)
                callback(AdLoadResult.Loaded)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                reportAdLoaded(
                    3, "interstitial", sceneName,
                    config.unitId, 0.0, "Admob", error.code, error.message, requestId
                )
//                AdEventTracker.reportLoadFailed(sceneName, error.code, error.message)
                callback(AdLoadResult.Failed(error.message,error.code))
//                callback(AdLoadResult.Failed("No fill.",3))
            }
        })
    }

    private fun fullScreenCallback(
        activity: AppCompatActivity,
        trackingType: String?,
        onPresented: () -> Unit,
        onFinished: () -> Unit,
        isAppOpen: Boolean
    ): FullScreenContentCallback {
        this.trackingType = trackingType
        return object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                displayed = true
                AidAdHub.log("$sceneName shown id=$requestId")
                AdEventTracker.reportImpression(sceneName, trackingType)
                onPresented()
            }

            override fun onAdClicked() {
//                AdEventTracker.reportClick(sceneName)
                runCatching {
                    if (isAppOpen) {
                        reportAdClick(
                            5, "splash", sceneName,
                            config.unitId, 0.0,
                            (sdkAd as AppOpenAd).responseInfo.loadedAdapterResponseInfo?.adSourceName
                                ?: "Admob", requestId
                        )
                    } else {
                        reportAdClick(
                            3,
                            "interstitial",
                            sceneName,
                            config.unitId,
                            0.0,
                            (sdkAd as InterstitialAd).responseInfo.loadedAdapterResponseInfo?.adSourceName
                                ?: "Admob",
                            requestId
                        )
                    }
                }
                ReminderTriggerCenter.onAdClicked()
            }

            override fun onAdDismissedFullScreenContent() {
                if (displayed) {
                    AidAdHub.markFullScreenClosed()
                }
                AidAdHub.log("$sceneName closed id=$requestId")
//                AdEventTracker.reportClose(sceneName)
                runCatching {
                    if (isAppOpen) {
                        reportAdClose(
                            5, "splash", sceneName,
                            config.unitId, 0,
                            (sdkAd as AppOpenAd).responseInfo.loadedAdapterResponseInfo?.adSourceName
                                ?: "Admob", requestId
                        )
                    } else {
                        reportAdClose(
                            3,
                            "interstitial",
                            sceneName,
                            config.unitId,
                            0,
                            (sdkAd as InterstitialAd).responseInfo.loadedAdapterResponseInfo?.adSourceName
                                ?: "Admob", requestId
                        )
                    }
                }
                continueWhenResumed(activity, onFinished)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AidAdHub.log("$sceneName show failed id=$requestId reason=${error.message}")
                runCatching {
                    if (isAppOpen) {
                        reportAdShowFailed(
                            5, "splash", sceneName,
                            config.unitId, 0,
                            (sdkAd as AppOpenAd).responseInfo.loadedAdapterResponseInfo?.adSourceName
                                ?: "Admob",
                            error.code, error.message, requestId
                        )
                    } else {
                        reportAdShowFailed(
                            3, "interstitial", sceneName,
                            config.unitId, 0,
                            (sdkAd as InterstitialAd).responseInfo.loadedAdapterResponseInfo?.adSourceName
                                ?: "Admob",
                            error.code, error.message, requestId
                        )
                    }
                }
//                AdEventTracker.reportImpressionFailed(sceneName, error.code, error.message)
                continueWhenResumed(activity, onFinished)
            }
        }
    }

    private fun continueWhenResumed(activity: AppCompatActivity, next: () -> Unit) {
        activity.lifecycleScope.launch {
            while (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                delay(100L)
            }
            next()
        }
    }

    private companion object {
        private const val INTERNAL_ERROR_CODE = -1
    }
}

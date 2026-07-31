package com.ysdc.aidpdf.ad.google

import android.content.Context
import android.util.Log
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

    override fun load(context: Context, callback: (AdLoadResult) -> Unit) {
        AdEventTracker.reportLoadStarted(sceneName)
        when (config.format) {
            AdFormat.Open -> loadAppOpen(context, callback)
            AdFormat.Interstitial -> loadInterstitial(context, callback)
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
            onFinished = request.onFinished
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
                    AdEventTracker.reportPaidValue(sceneName, config, value, ad.responseInfo)
                }
                sdkAd = ad
                loadedAtMillis = System.currentTimeMillis()
                AdEventTracker.reportLoadSucceeded(sceneName)
                callback(AdLoadResult.Loaded)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                AdEventTracker.reportLoadFailed(sceneName, error.code, error.message)
                callback(AdLoadResult.Failed(error.message))
            }
        })
    }

    private fun loadInterstitial(context: Context, callback: (AdLoadResult) -> Unit) {
        AidAdHub.log("$sceneName interstitial loading id=$requestId")
        InterstitialAd.load(context, config.unitId, request, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                ad.setOnPaidEventListener { value ->
                    AidAdHub.log("$sceneName paid value=${value.valueMicros} currency=${value.currencyCode}")
                    AdEventTracker.reportPaidValue(sceneName, config, value, ad.responseInfo)
                }
                sdkAd = ad
                loadedAtMillis = System.currentTimeMillis()
                AdEventTracker.reportLoadSucceeded(sceneName)
                callback(AdLoadResult.Loaded)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                AdEventTracker.reportLoadFailed(sceneName, error.code, error.message)
                callback(AdLoadResult.Failed(error.message))
            }
        })
    }

    private fun fullScreenCallback(
        activity: AppCompatActivity,
        trackingType: String?,
        onPresented: () -> Unit,
        onFinished: () -> Unit
    ): FullScreenContentCallback {
        return object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                displayed = true
                AidAdHub.log("$sceneName shown id=$requestId")
                AdEventTracker.reportImpression(sceneName, trackingType)
                onPresented()
            }

            override fun onAdClicked() {
                AdEventTracker.reportClick(sceneName)
                ReminderTriggerCenter.onAdClicked()
            }

            override fun onAdDismissedFullScreenContent() {
                if (displayed) {
                    AidAdHub.markFullScreenClosed()
                }
                AidAdHub.log("$sceneName closed id=$requestId")
                AdEventTracker.reportClose(sceneName)
                continueWhenResumed(activity, onFinished)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AidAdHub.log("$sceneName show failed id=$requestId reason=${error.message}")
                AdEventTracker.reportImpressionFailed(sceneName, error.code, error.message)
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

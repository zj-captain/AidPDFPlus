package com.ysdc.aidpdf.tracking

import android.app.Application
import android.util.Log
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAdRevenue
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.LogLevel
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.ysdc.aidpdf.BuildConfig

internal object AdjustInitializer {

    private const val TAG = "AdjustInitializer"

    @Volatile
    private var initialized = false

    fun initialize(application: Application) {
        runCatching {
            val customerUserId = VertSDK.getDistinctId()
            Adjust.addGlobalCallbackParameter("customer_user_id", customerUserId)
            val environment = if (BuildConfig.DEBUG) {
                AdjustConfig.ENVIRONMENT_SANDBOX
            } else {
                AdjustConfig.ENVIRONMENT_PRODUCTION
            }
            val appToken = "rjwx33oxsao0"
            val config = AdjustConfig(application, appToken, environment).apply {
                setLogLevel(if (BuildConfig.DEBUG) LogLevel.VERBOSE else LogLevel.WARN)
            }
            Adjust.initSdk(config)
            initialized = true
            debugLog("Adjust initialized")
        }.onFailure {
            debugLog("Adjust initialization failed: ${it.message}")
        }
    }

    fun onActivityResumed() {
        if (initialized) runCatching(Adjust::onResume)
    }

    fun onActivityPaused() {
        if (initialized) runCatching(Adjust::onPause)
    }

    fun trackAdRevenue(adValue: AdValue, responseInfo: ResponseInfo?) {
        if (!initialized) return
        runCatching {
            val revenue = AdjustAdRevenue("admob_sdk").apply {
                setRevenue(adValue.valueMicros / 1_000_000.0, adValue.currencyCode)
                adRevenueNetwork = responseInfo?.loadedAdSourceResponseInfo?.name.orEmpty()
            }
            Adjust.trackAdRevenue(revenue)
        }.onFailure {
            debugLog("Adjust ad revenue failed: ${it.message}")
        }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}

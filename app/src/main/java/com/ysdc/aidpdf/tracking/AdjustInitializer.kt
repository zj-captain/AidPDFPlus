package com.ysdc.aidpdf.tracking

import android.app.Application
import android.util.Log
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.AdjustAdRevenue
import com.adjust.sdk.LogLevel
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo
import com.ysdc.aidpdf.BuildConfig

internal object AdjustInitializer {

    private const val TAG = "AdjustInitializer"
    private const val CUSTOMER_USER_ID = "customer_user_id"

    @Volatile
    private var initialized = false

    fun initialize(application: Application, customerUserId: String?) {
        val appToken = BuildConfig.ADJUST_APP_TOKEN.trim()
        if (appToken.isEmpty()) {
            debugLog("Adjust is disabled because no app token is configured")
            return
        }

        runCatching {
            customerUserId
                ?.takeIf(String::isNotBlank)
                ?.let { Adjust.addGlobalCallbackParameter(CUSTOMER_USER_ID, it) }

            val environment = if (BuildConfig.DEBUG) {
                AdjustConfig.ENVIRONMENT_SANDBOX
            } else {
                AdjustConfig.ENVIRONMENT_PRODUCTION
            }
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
                adRevenueNetwork = responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty()
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

package com.ysdc.aidpdf.ad

import android.os.Bundle
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAdRevenue
import com.facebook.appevents.AppEventsLogger
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.firebase.analytics.FirebaseAnalytics
import com.loft.vertsdk.VertSDK
import com.ysdc.aidpdf.BuildConfig
import com.ysdc.aidpdf.ads.config.AdsFormat
import com.ysdc.aidpdf.ads.config.AdsUnitConfig
import com.ysdc.aidpdf.store.appInstance
import com.ysdc.aidpdf.store.currentAdRevenue
import com.ysdc.aidpdf.tracking.AdjustInitializer
import com.ysdc.aidpdf.tracking.AidEventHub
import com.ysdc.aidpdf.tracking.AidEventHub.firebaseAnalytics
import com.ysdc.aidpdf.tracking.TrackingEventNames
import org.json.JSONObject
import java.util.Currency

object AdEventTracker {
    val facebookLogger by lazy { AppEventsLogger.newLogger(appInstance) }

    fun reportPaidValue(
        scene: String,
        config: AdsUnitConfig,
        adValue: AdValue,
        responseInfo: ResponseInfo?
    ) {
        val sourceName = responseInfo?.loadedAdSourceResponseInfo?.name.orEmpty()
        AidEventHub.trackFirebaseOnly(
            eventName = TrackingEventNames.AD_IMPRESSION_REVENUE,
            parameters = firebaseRevenueParameters(
                scene = scene,
                format =resolveAdTypeName(config.format),
                unitId = config.unitId,
                valueMicros = adValue.valueMicros,
                currencyCode = adValue.currencyCode,
                precisionType = adValue.precisionType.ordinal,
                sourceName = sourceName
            )
        )
        AdjustInitializer.trackAdRevenue(adValue, responseInfo)

        val revenue = adValue.valueMicros / 1_000_000.0
        runCatching {
            facebookLogger.logPurchase(
                revenue.toBigDecimal(),
                Currency.getInstance("USD")
            )
        }
    }
    fun sendTpRevenue(ecpm: Double, currencyCode: String?, responseInfoName: String?) {
        runCatching {
            Adjust.trackAdRevenue(AdjustAdRevenue("admob_sdk").also {
                it.setRevenue(ecpm * 1000, currencyCode)
                it.adRevenueNetwork = responseInfoName
            })
        }

        if (!BuildConfig.DEBUG) {
            runCatching {
                firebaseAnalytics?.logEvent("ad_impression_revenue", Bundle().apply {
                    putDouble(FirebaseAnalytics.Param.VALUE, ecpm * 1000)
                    putString(FirebaseAnalytics.Param.CURRENCY, "USD")
                })
            }

            runCatching {
                facebookLogger.logPurchase(
                    (ecpm * 1000).toBigDecimal(),
                    Currency.getInstance("USD")
                )
            }
        }
    }
    fun reportTotalAdsRenenue001Admob(adValue: AdValue) {
        //新增Total_Ads_Renenue_001 投放事件上报
        runCatching {
            val revenueTemp = adValue.valueMicros / 1_000_000.0
//            val revenueTemp = Random.nextInt(900, 10000) / 1_000_000.0f
//            Log.e("AidEventHub", "onAdPaid: revenueTemp = $revenueTemp")
            var current = currentAdRevenue
            current += revenueTemp
//            Log.e("AidEventHub", "sendAdRevenue: current = $current")
            if (current >= 0.01f) {
//                Log.e("AidEventHub", "reportTotalAdsRenenue001")
                val analytics = firebaseAnalytics ?: return
                analytics.logEvent("Total_Ads_Renenue_001", Bundle().apply {
                    putDouble(FirebaseAnalytics.Param.VALUE, current)
                    putString(FirebaseAnalytics.Param.CURRENCY, "USD")
                })

                val jsonObject = JSONObject()
                jsonObject.put(FirebaseAnalytics.Param.VALUE, current)
                jsonObject.put(FirebaseAnalytics.Param.CURRENCY, "USD")
                VertSDK.trackEvent("Total_Ads_Renenue_001", jsonObject)

                currentAdRevenue = 0.000000000000
            } else {
                currentAdRevenue = current
            }
        }
    }
    fun reportTotalAdsRenenue001Tp(ecmp: Double) {
        //新增Total_Ads_Renenue_001 投放事件上报
        runCatching {
            val revenueTemp = ecmp / 1000
            var current = currentAdRevenue
            current += revenueTemp
            if (current >= 0.01f) {
                val analytics = firebaseAnalytics ?: return
                analytics.logEvent("Total_Ads_Renenue_001", Bundle().apply {
                    putDouble(FirebaseAnalytics.Param.VALUE, current)
                    putString(FirebaseAnalytics.Param.CURRENCY, "USD")
                })

                val jsonObject = JSONObject()
                jsonObject.put(FirebaseAnalytics.Param.VALUE, current)
                jsonObject.put(FirebaseAnalytics.Param.CURRENCY, "USD")
                VertSDK.trackEvent("Total_Ads_Renenue_001", jsonObject)

                currentAdRevenue = 0.000000000000
            } else {
                currentAdRevenue = current
            }
        }
    }
    internal fun firebaseRevenueParameters(
        scene: String,
        format: String,
        unitId: String,
        valueMicros: Long,
        currencyCode: String,
        precisionType: Int,
        sourceName: String
    ): Map<String, Any?> {
        val revenue = valueMicros / MICROS_PER_CURRENCY_UNIT
        return mapOf(
            "value" to revenue,
            "currency" to ACEC_FIREBASE_CURRENCY,
            "scene" to scene,
            "ad_platform" to ADMOB_PLATFORM,
            "ad_source" to sourceName.ifBlank { ADMOB_PLATFORM },
            "ad_format" to format,
            "ad_unit_name" to unitId,
            "ad_value" to revenue,
            "ad_currency" to currencyCode,
            "value_micros" to valueMicros,
            "precision" to precisionType
        )
    }
    private fun resolveAdTypeName(format: AdsFormat): String = when (format) {
        AdsFormat.Banner -> "banner"
        AdsFormat.Interstitial -> "interstitial"
        AdsFormat.Open -> "splash"
        AdsFormat.Native -> "native"
    }
    private const val ADMOB_PLATFORM = "Admob"
    private const val ACEC_FIREBASE_CURRENCY = "USD"
    private const val MICROS_PER_CURRENCY_UNIT = 1_000_000.0
}

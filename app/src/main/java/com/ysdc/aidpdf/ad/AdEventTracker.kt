package com.ysdc.aidpdf.ad

import android.os.Bundle
import android.util.Log
import com.facebook.appevents.AppEventsLogger
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo
import com.google.firebase.analytics.FirebaseAnalytics
import com.loft.vertsdk.VertSDK
import com.ysdc.aidpdf.ad.config.AdFormat
import com.ysdc.aidpdf.ad.config.AdUnitConfig
import com.ysdc.aidpdf.store.appInstance
import com.ysdc.aidpdf.store.currentAdRevenue
import com.ysdc.aidpdf.tracking.AdjustInitializer
import com.ysdc.aidpdf.tracking.AidEventHub
import com.ysdc.aidpdf.tracking.AidEventHub.firebaseAnalytics
import com.ysdc.aidpdf.tracking.EventDelivery
import com.ysdc.aidpdf.tracking.TrackingEventNames
import org.json.JSONObject
import java.util.Currency
import kotlin.random.Random

object AdEventTracker {

    fun reportLoadStarted(scene: String) {
        report(TrackingEventNames.AD_PLACEMENT_REQUEST, scene)
    }

    fun reportLoadSucceeded(scene: String) {
        report(TrackingEventNames.AD_SOURCE_REQUEST, scene)
    }

    fun reportLoadFailed(scene: String, code: Int, message: String) {
        report(
            TrackingEventNames.AD_SOURCE_ERROR,
            scene,
            mapOf("result_code" to code, "result_info" to message)
        )
    }

    fun reportChance(scene: String, type: String? = null) {
        report(TrackingEventNames.AD_CHANCE, scene, typeParameters(type))
    }

    fun reportImpression(scene: String, type: String? = null) {
        val parameters = typeParameters(type)
//        report(TrackingEventNames.AD_IMPRESSION, scene, parameters)
        report(TrackingEventNames.ADMOB_IMPRESSION, scene)
    }

    fun reportImpressionFailed(scene: String, code: Int, message: String) {
        report(
            TrackingEventNames.AD_IMPRESSION_ERROR,
            scene,
            mapOf("result_code" to code, "result_info" to message)
        )
    }

    fun reportClick(scene: String) {
        report(TrackingEventNames.AD_CLICK, scene)
    }

    fun reportClose(scene: String) {
        report(TrackingEventNames.AD_CLOSE, scene)
    }

    val facebookLogger by lazy { AppEventsLogger.newLogger(appInstance) }

    fun reportPaidValue(
        scene: String,
        config: AdUnitConfig,
        adValue: AdValue,
        responseInfo: ResponseInfo?
    ) {
        val sourceName = responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty()
        AidEventHub.trackFirebaseOnly(
            eventName = TrackingEventNames.AD_IMPRESSION_REVENUE,
            parameters = firebaseRevenueParameters(
                scene = scene,
                format = config.format,
                unitId = config.unitId,
                valueMicros = adValue.valueMicros,
                currencyCode = adValue.currencyCode,
                precisionType = adValue.precisionType,
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

    fun reportTotalAdsRenenue001(adValue: AdValue) {
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

    internal fun firebaseRevenueParameters(
        scene: String,
        format: AdFormat,
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
            "ad_format" to format.analyticsValue(),
            "ad_unit_name" to unitId,
            "ad_value" to revenue,
            "ad_currency" to currencyCode,
            "value_micros" to valueMicros,
            "precision" to precisionType
        )
    }

    private fun report(
        eventName: String,
        scene: String,
        extraParameters: Map<String, Any?> = emptyMap()
    ) {
        AidEventHub.track(
            eventName = eventName,
            parameters = buildMap {
                put("scene", scene)
                putAll(extraParameters)
            },
            delivery = EventDelivery.Batched
        )
    }

    private fun typeParameters(type: String?): Map<String, String> {
        return type?.takeIf(String::isNotBlank)?.let { mapOf("type" to it) }.orEmpty()
    }

    private fun AdFormat.analyticsValue(): String = when (this) {
        AdFormat.Open -> "app_open"
        AdFormat.Interstitial -> "interstitial"
        AdFormat.Native -> "native"
        AdFormat.Banner -> "banner"
    }

    private const val ADMOB_PLATFORM = "Admob"
    private const val ACEC_FIREBASE_CURRENCY = "USD"
    private const val ACEC_FIREBASE_VALUE_SCALE = 1_000.0
    private const val MICROS_PER_CURRENCY_UNIT = 1_000_000.0
}

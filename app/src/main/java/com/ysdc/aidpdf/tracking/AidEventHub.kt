package com.ysdc.aidpdf.tracking

import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.loft.vertsdk.InitializationCallback
import com.loft.vertsdk.VertConfiguration
import com.loft.vertsdk.VertSDK
import com.ysdc.aidpdf.BuildConfig
import com.ysdc.aidpdf.tracking.TrackingEventNames.ADMOB_IMPRESSION
import com.ysdc.aidpdf.tracking.TrackingEventNames.AD_CLICK
import com.ysdc.aidpdf.tracking.TrackingEventNames.AD_CLOSE
import com.ysdc.aidpdf.tracking.TrackingEventNames.AD_IMPRESSION
import com.ysdc.aidpdf.tracking.TrackingEventNames.AD_IMPRESSION_ERROR
import com.ysdc.aidpdf.tracking.TrackingEventNames.AD_PLACEMENT_REQUEST
import com.ysdc.aidpdf.tracking.TrackingEventNames.AD_SOURCE_ERROR
import com.ysdc.aidpdf.tracking.TrackingEventNames.AD_SOURCE_REQUEST
import org.json.JSONObject

enum class EventDelivery {
    Immediate,
    Batched
}

object AidEventHub {

    private const val TAG = "AidEventHub"

    @Volatile
    private var initialized = false
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var vertAvailable = false

    fun initialize(application: Application) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initializeFirebase(application)
            initializeVert(application)
            initialized = true
        }
    }

    fun track(
        eventName: String,
        parameters: Map<String, Any?> = emptyMap(),
        delivery: EventDelivery = EventDelivery.Immediate
    ) {
        val event = EventPayloadNormalizer.normalize(eventName, parameters)
        if (event == null) {
            debugLog("Ignored invalid event name: $eventName")
            return
        }
        if (!initialized) {
            debugLog("Ignored event before initialization: ${event.name}")
            return
        }

        val value = if (parameters.keys.isNotEmpty()) {
            val temp = StringBuffer()
            parameters.values.forEach {
                temp.append(it).append("  ")
            }
            temp.toString()
        } else {
            null
        }
        if (value == null) {
            myLog(TAG, "reportEvent: eventName = $eventName")
        } else {
            myLog(TAG, "reportEvent: eventName = $eventName value = $value")
        }

        reportToFirebase(event)
        reportToVert(event, delivery)
    }

    internal fun trackFirebaseOnly(eventName: String, parameters: Map<String, Any?>) {
        if (!initialized) return
        val event = EventPayloadNormalizer.normalize(eventName, parameters) ?: return
        reportToFirebase(event)
    }

    private fun initializeFirebase(application: Application) {
        firebaseAnalytics = runCatching {
            FirebaseApp.initializeApp(application)
            if (FirebaseApp.getApps(application).isEmpty()) {
                null
            } else {
                FirebaseAnalytics.getInstance(application)
            }
        }.onFailure {
            debugLog("Firebase Analytics initialization failed: ${it.message}")
        }.getOrNull()
    }

    private fun initializeVert(application: Application) {
        val productId = BuildConfig.TRACKING_PRODUCT_ID.trim()
        if (productId.isEmpty()) {
            debugLog("Vert tracking is disabled because no product ID is configured")
            return
        }

        runCatching {
            val configuration = VertConfiguration.Builder(productId, BuildConfig.TRACKING_HOST)
                .channel(BuildConfig.TRACKING_CHANNEL)
                .debugMode(BuildConfig.DEBUG)
                .enableLog(BuildConfig.DEBUG)
                .analyticsProperties(JSONObject().put("prd_id", productId))
                .build()

            VertSDK.initialize(application, configuration, object : InitializationCallback {
                override fun onSuccess(data: JSONObject?) {
                    debugLog("Vert tracking initialized")

                    AdjustInitializer.initialize(application, vertDistinctId())
                }

                override fun onFailure(error: String) {
                    debugLog("Vert tracking authentication failed: $error")
                }
            })
            vertAvailable = true
        }.onFailure {
            debugLog("Vert tracking initialization failed: ${it.message}")
        }
    }

    private fun reportToFirebase(event: EventPayload) {
        if (BuildConfig.DEBUG) return
        val analytics = firebaseAnalytics ?: return
        runCatching {
            if (event.name != ADMOB_IMPRESSION){
                analytics.logEvent(event.name, event.parameters.toBundle())
            }
        }.onFailure {
            debugLog("Firebase event failed: ${event.name}, ${it.message}")
        }
    }

    private fun vertDistinctId(): String? {
        if (!vertAvailable) return null
        return runCatching(VertSDK::getDistinctId).getOrNull()
    }

    private fun reportToVert(event: EventPayload, delivery: EventDelivery) {
        if (!vertAvailable) return
        runCatching {
            val properties = event.parameters.toJson()
            when (delivery) {
                EventDelivery.Immediate -> VertSDK.trackEvent(event.name, properties)
                EventDelivery.Batched -> VertSDK.trackBatchEvent(event.name, properties)
            }
        }.onFailure {
            debugLog("Vert event failed: ${event.name}, ${it.message}")
        }
    }

    private fun Map<String, Any>.toBundle(): Bundle? {
        if (isEmpty()) return null
        return Bundle().apply {
            forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                }
            }
        }
    }

    private fun Map<String, Any>.toJson(): JSONObject? {
        if (isEmpty()) return null
        return JSONObject().apply {
            forEach { (key, value) -> put(key, value) }
        }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun myLog(tag: String,message: String){
        if (BuildConfig.DEBUG){
            Log.e(tag, message)
        }
    }

    fun reportStartLoading(
        adType: Int,
        adTypeName: String,
        scene: String,
        adId: String,
        sessionId: String
    ) {
        track(
            AD_PLACEMENT_REQUEST, mapOf(
                "ad_type" to adType,
                "ad_type_name" to adTypeName,
                "scene" to scene,
                "ad_mediation" to "Admob",
                "ad_placement_id" to adId,
                "session_id" to sessionId
            ), EventDelivery.Batched
        )
    }

    fun reportAdLoaded(
        adType: Int,
        adTypeName: String,
        scene: String,
        adId: String,
        adEcpmNumber: Double,
        adSource: String,
        resultCode: Int,
        resultInfo: String,
        sessionId: String
    ) = runCatching {
        track(
            if (resultCode == 200) AD_SOURCE_REQUEST else AD_SOURCE_ERROR,
            mapOf(
                "ad_type" to adType,
                "ad_type_name" to adTypeName,
                "scene" to scene,
                "ad_mediation" to "Admob",
                "ad_placement_name" to "",
                "ad_placement_id" to adId,
                "ad_ecpm_number" to adEcpmNumber,
                "ad_source" to adSource,
                "ad_source_id" to adId,
                "result_code" to resultCode,
                "result_info" to resultInfo,
                "session_id" to sessionId
            ),EventDelivery.Batched
        )
    }


    fun reportAdShow(
        adType: Int,
        adTypeName: String,
        scene: String,
        adId: String,
        adEcpmNumber: Double,
        adSource: String,
        resultCode: Int,
        resultInfo: String,
        sessionId: String,
        type: String? = null
    ) = runCatching {
        if (type == null) {
            track(
                AD_IMPRESSION, mapOf(
                    "ad_type" to adType,
                    "ad_type_name" to adTypeName,
                    "scene" to scene,
                    "ad_mediation" to "Admob",
                    "ad_placement_name" to "",
                    "ad_placement_id" to adId,
                    "ad_ecpm_number" to adEcpmNumber,
                    "ad_source" to adSource,
                    "ad_source_id" to adId,
                    "result_code" to resultCode,
                    "result_info" to resultInfo,
                    "session_id" to sessionId
                ),EventDelivery.Batched
            )
        } else {
            track(
                AD_IMPRESSION, mapOf(
                    "ad_type" to adType,
                    "ad_type_name" to adTypeName,
                    "scene" to scene,
                    "ad_mediation" to "Admob",
                    "ad_placement_name" to "",
                    "ad_placement_id" to adId,
                    "ad_ecpm_number" to adEcpmNumber,
                    "ad_source" to adSource,
                    "ad_source_id" to adId,
                    "result_code" to resultCode,
                    "result_info" to resultInfo,
                    "session_id" to sessionId,
                    "type" to type
                ),EventDelivery.Batched
            )
        }
    }

    fun reportAdShowFailed(
        adType: Int,
        adTypeName: String,
        scene: String,
        adId: String,
        adEcpmNumber: Long,
        adSource: String,
        resultCode: Int,
        resultInfo: String,
        sessionId: String
    ) = runCatching {
        track(
            AD_IMPRESSION_ERROR, mapOf(
                "ad_type" to adType,
                "ad_type_name" to adTypeName,
                "scene" to scene,
                "ad_mediation" to "Admob",
                "ad_placement_name" to "",
                "ad_placement_id" to adId,
                "ad_ecpm_number" to adEcpmNumber,
                "ad_source" to adSource,
                "ad_source_id" to adId,
                "result_code" to resultCode,
                "result_info" to resultInfo,
                "session_id" to sessionId
            ),EventDelivery.Batched
        )
    }

    fun reportAdClick(
        adType: Int,
        adTypeName: String,
        scene: String,
        adId: String,
        adEcpmNumber: Double,
        adSource: String,
        sessionId: String
    ) = runCatching {
        track(
            AD_CLICK, mapOf(
                "ad_type" to adType,
                "ad_type_name" to adTypeName,
                "scene" to scene,
                "ad_mediation" to "Admob",
                "ad_placement_name" to "",
                "ad_placement_id" to adId,
                "ad_ecpm_number" to adEcpmNumber,
                "ad_source" to adSource,
                "ad_source_id" to adId,
                "session_id" to sessionId
            ),EventDelivery.Batched
        )
    }

    fun reportAdClose(
        adType: Int,
        adTypeName: String,
        scene: String,
        adId: String,
        adEcpmNumber: Long,
        adSource: String,
        sessionId: String
    ) = runCatching {
        track(
            AD_CLOSE, mapOf(
                "ad_type" to adType,
                "ad_type_name" to adTypeName,
                "scene" to scene,
                "ad_mediation" to "Admob",
                "ad_placement_name" to "",
                "ad_placement_id" to adId,
                "ad_ecpm_number" to adEcpmNumber,
                "ad_source" to adSource,
                "ad_source_id" to adId,
                "session_id" to sessionId
            ),EventDelivery.Batched
        )
    }

}

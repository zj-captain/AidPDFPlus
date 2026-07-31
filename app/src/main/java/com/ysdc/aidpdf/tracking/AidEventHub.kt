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
            AdjustInitializer.initialize(application, vertDistinctId())
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
}

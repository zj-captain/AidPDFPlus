package com.ysdc.aidpdf.ads.core

import com.ysdc.aidpdf.ads.config.AdsFormat
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsUnitConfig
import com.ysdc.aidpdf.tracking.AidEventHub
import com.ysdc.aidpdf.tracking.EventDelivery
import com.ysdc.aidpdf.tracking.TrackingEventNames
import java.util.UUID

/**
 * 广告事件埋点封装。
 * 只负责把广告生命周期事件上报给 AidEventHub，不参与广告缓存/竞价/展示等业务逻辑。
 *
 * 字段口径：
 * - ad_type / ad_type_name：2=banner、3=interstitial、5=splash、7=native
 * - scene：加载事件直接传广告位 remoteKey；展示事件优先传 trackingScene，为空回退 remoteKey
 * - ad_mediation：聚合平台标识（Admob / TradPlus）
 * - ad_placement_id：广告单元 id
 * - session_id：UUID 去横线（与原 requestId 生成方式一致）
 */
object AdsEventTracker {

    private const val RESULT_SUCCESS = 200
    private const val RESULT_FAILED = 0
    private const val EMPTY_ECPM = 0.0

    fun reportLoadStarted(config: AdsUnitConfig) {
        AidEventHub.track(
            TrackingEventNames.AD_PLACEMENT_REQUEST,
            mapOf(
                "ad_type" to resolveAdType(config.format),
                "ad_type_name" to resolveAdTypeName(config.format),
                "scene" to config.scene.remoteKey,
                "ad_mediation" to resolveMediation(config.platform),
                "ad_placement_id" to config.unitId,
                "session_id" to newSessionId()
            ),
            EventDelivery.Batched
        )
    }

    fun reportLoaded(config: AdsUnitConfig, success: Boolean, resultCode: Int, resultInfo: String) {
        AidEventHub.track(
            if (success) TrackingEventNames.AD_SOURCE_REQUEST else TrackingEventNames.AD_SOURCE_ERROR,
            mapOf(
                "ad_type" to resolveAdType(config.format),
                "ad_type_name" to resolveAdTypeName(config.format),
                "scene" to config.scene.remoteKey,
                "ad_mediation" to resolveMediation(config.platform),
                "ad_placement_name" to "",
                "ad_placement_id" to config.unitId,
                "ad_ecpm_number" to EMPTY_ECPM,
                "ad_source" to resolveMediation(config.platform),
                "ad_source_id" to config.unitId,
                "result_code" to resultCode,
                "result_info" to resultInfo,
                "session_id" to newSessionId()
            ),
            EventDelivery.Batched
        )
    }

    fun reportShown(
        config: AdsUnitConfig,
        trackingScene: String?,
        trackingType: String? = null,
        ecpm: Double? = null
    ) {
        val params = mutableMapOf<String, Any?>(
            "ad_type" to resolveAdType(config.format),
            "ad_type_name" to resolveAdTypeName(config.format),
            "scene" to resolveScene(config, trackingScene),
            "ad_mediation" to resolveMediation(config.platform),
            "ad_placement_name" to "",
            "ad_placement_id" to config.unitId,
            "ad_ecpm_number" to (ecpm ?: EMPTY_ECPM),
            "ad_source" to resolveMediation(config.platform),
            "ad_source_id" to config.unitId,
            "result_code" to RESULT_SUCCESS,
            "result_info" to "",
            "session_id" to newSessionId()
        )
        trackingType?.takeIf { it.isNotBlank() }?.let { params["type"] = it }
        AidEventHub.track(TrackingEventNames.AD_IMPRESSION, params, EventDelivery.Batched)
    }

    fun reportShowFailed(config: AdsUnitConfig, trackingScene: String?, error: AdsExceptionInfo) {
        AidEventHub.track(
            TrackingEventNames.AD_IMPRESSION_ERROR,
            mapOf(
                "ad_type" to resolveAdType(config.format),
                "ad_type_name" to resolveAdTypeName(config.format),
                "scene" to resolveScene(config, trackingScene),
                "ad_mediation" to resolveMediation(config.platform),
                "ad_placement_name" to "",
                "ad_placement_id" to config.unitId,
                "ad_ecpm_number" to EMPTY_ECPM,
                "ad_source" to resolveMediation(config.platform),
                "ad_source_id" to config.unitId,
                "result_code" to error.code.ordinal,
                "result_info" to error.message,
                "session_id" to newSessionId()
            ),
            EventDelivery.Batched
        )
    }

    fun reportClick(config: AdsUnitConfig, trackingScene: String?) {
        AidEventHub.track(
            TrackingEventNames.AD_CLICK,
            mapOf(
                "ad_type" to resolveAdType(config.format),
                "ad_type_name" to resolveAdTypeName(config.format),
                "scene" to resolveScene(config, trackingScene),
                "ad_mediation" to resolveMediation(config.platform),
                "ad_placement_name" to "",
                "ad_placement_id" to config.unitId,
                "ad_ecpm_number" to EMPTY_ECPM,
                "ad_source" to resolveMediation(config.platform),
                "ad_source_id" to config.unitId,
                "session_id" to newSessionId()
            ),
            EventDelivery.Batched
        )
    }

    fun reportClosed(config: AdsUnitConfig, trackingScene: String?) {
        AidEventHub.track(
            TrackingEventNames.AD_CLOSE,
            mapOf(
                "ad_type" to resolveAdType(config.format),
                "ad_type_name" to resolveAdTypeName(config.format),
                "scene" to resolveScene(config, trackingScene),
                "ad_mediation" to resolveMediation(config.platform),
                "ad_placement_name" to "",
                "ad_placement_id" to config.unitId,
                "ad_ecpm_number" to EMPTY_ECPM,
                "ad_source" to resolveMediation(config.platform),
                "ad_source_id" to config.unitId,
                "session_id" to newSessionId()
            ),
            EventDelivery.Batched
        )
    }

    private fun resolveAdType(format: AdsFormat): Int = when (format) {
        AdsFormat.Banner -> 2
        AdsFormat.Interstitial -> 3
        AdsFormat.Open -> 5
        AdsFormat.Native -> 7
    }

    private fun resolveAdTypeName(format: AdsFormat): String = when (format) {
        AdsFormat.Banner -> "banner"
        AdsFormat.Interstitial -> "interstitial"
        AdsFormat.Open -> "splash"
        AdsFormat.Native -> "native"
    }

    private fun resolveMediation(platform: AdsPlatform): String = when (platform) {
        AdsPlatform.AdMob -> "Admob"
        AdsPlatform.TradPlus -> "TradPlus"
    }

    private fun resolveScene(config: AdsUnitConfig, trackingScene: String?): String {
        return trackingScene?.takeIf { it.isNotBlank() } ?: config.scene.remoteKey
    }

    private fun newSessionId(): String = UUID.randomUUID().toString().replace("-", "")
}

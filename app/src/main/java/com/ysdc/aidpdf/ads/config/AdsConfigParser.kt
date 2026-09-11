package com.ysdc.aidpdf.ads.config

import com.ysdc.aidpdf.ads.core.AdsLogger
import org.json.JSONObject

object AdsConfigParser {

    private const val DEFAULT_TTL_SECONDS = 1200

    fun parse(json: String, fallback: AdsCatalog = AdsCatalog()): AdsCatalog {
        return runCatching {
            val root = JSONObject(json)
            val parsed = AdsScene.entries.associateWith { scene ->
                parseItems(root, scene, fallback.unitsFor(scene))
            }
            AdsCatalog.from(parsed)
        }.onFailure {
            AdsLogger.w("广告配置解析失败，使用回退配置：${it.message}")
        }.getOrDefault(fallback)
    }

    private fun parseItems(
        root: JSONObject,
        scene: AdsScene,
        fallback: List<AdsUnitConfig>
    ): List<AdsUnitConfig> {
        val array = root.optJSONArray(scene.remoteKey) ?: return fallback
        val parsed = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                parseItem(scene, item)?.let { add(it) }
            }
        }
        return parsed.ifEmpty { fallback }
    }

    private fun parseItem(scene: AdsScene, item: JSONObject): AdsUnitConfig? {
        val platform = AdsPlatform.fromRemote(item.optString("ad_paltfrom"))
        if (platform == null) {
            AdsLogger.w("广告配置平台无效，场景=${scene.remoteKey}")
            return null
        }
        val format = AdsFormat.fromRemote(item.optString("ad_type"))
        if (format == null || format != scene.expectedFormat) {
            AdsLogger.w("广告配置类型无效，场景=${scene.remoteKey}，期望=${scene.expectedFormat}")
            return null
        }
        val unitId = item.optString("ad_unit_id").trim()
        if (unitId.isBlank()) {
            AdsLogger.w("广告配置广告位为空，场景=${scene.remoteKey}")
            return null
        }
        return AdsUnitConfig(
            scene = scene,
            unitId = unitId,
            platform = platform,
            format = format,
            ttlSeconds = item.optInt("ad_timelimit", DEFAULT_TTL_SECONDS).coerceAtLeast(1)
        )
    }
}

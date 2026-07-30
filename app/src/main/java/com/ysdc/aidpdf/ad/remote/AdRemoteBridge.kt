package com.ysdc.aidpdf.ad.remote

import android.util.Log
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdCatalog
import com.ysdc.aidpdf.ad.config.AdFormat
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.config.AdUnitConfig
import com.ysdc.aidpdf.remote.RemoteConfigUtils
import org.json.JSONObject

object AdRemoteBridge {

    private const val REMOTE_AD_CONFIG_KEY = "ac_ad_config"

    const val DEFAULT_AD_CONFIG_JSON = """
{
  "ac_launch": [
    {
      "ad_unit_id": "ca-app-pub-3940256099942544/9257395921",
      "ad_paltfrom": "admob",
      "ad_type": "op",
      "ad_timelimit": 1200
    }
  ],
  "ac_bottom_int": [
    {
      "ad_unit_id": "ca-app-pub-3940256099942544/1033173712",
      "ad_paltfrom": "admob",
      "ad_type": "int",
      "ad_timelimit": 1200
    }
  ],
  "ac_top_int": [
    {
      "ad_unit_id": "ca-app-pub-3940256099942544/1033173712",
      "ad_paltfrom": "admob",
      "ad_type": "int",
      "ad_timelimit": 1200
    }
  ],
  "ac_check_int": [
    {
      "ad_unit_id": "ca-app-pub-3940256099942544/1033173712",
      "ad_paltfrom": "admob",
      "ad_type": "int",
      "ad_timelimit": 1200
    }
  ],
  "ac_mainback_int": [
    {
      "ad_unit_id": "ca-app-pub-3940256099942544/1033173712",
      "ad_paltfrom": "admob",
      "ad_type": "int",
      "ad_timelimit": 1200
    }
  ],
  "ac_main_nat": [
    {
      "ad_unit_id": "ca-app-pub-3940256099942544/2247696110",
      "ad_paltfrom": "admob",
      "ad_type": "nat",
      "ad_timelimit": 1200
    }
  ],
  "ac_main_banner": [
    {
      "ad_unit_id": "ca-app-pub-3940256099942544/9214589741",
      "ad_paltfrom": "admob",
      "ad_type": "banner",
      "ad_timelimit": 1200
    }
  ],
  "ac_uninstall1_nat": [
    {
      "ad_unit_id": "ca-app-pub-3940256099942544/2247696110",
      "ad_paltfrom": "admob",
      "ad_type": "nat",
      "ad_timelimit": 1200
    }
  ],
  "ac_uninstall1_int": [
    {
      "ad_unit_id": "ca-app-pub-3940256099942544/1033173712",
      "ad_paltfrom": "admob",
      "ad_type": "int",
      "ad_timelimit": 1200
    }
  ],
  "ac_uninstall2_nat": [
    {
      "ad_unit_id": "ca-app-pub-3940256099942544/2247696110",
      "ad_paltfrom": "admob",
      "ad_type": "nat",
      "ad_timelimit": 1200
    }
  ],
  "ac_uninstall2_int": [
    {
      "ad_unit_id": "ca-app-pub-3940256099942544/1033173712",
      "ad_paltfrom": "admob",
      "ad_type": "int",
      "ad_timelimit": 1200
    }
  ],
  "ac_result_nat": [
    {
      "ad_unit_id": "ca-app-pub-3940256099942544/2247696110",
      "ad_paltfrom": "admob",
      "ad_type": "nat",
      "ad_timelimit": 1200
    }
  ]
}
"""

    private val localCatalog: AdCatalog by lazy {
        parseCatalog(DEFAULT_AD_CONFIG_JSON, AdCatalog())
    }

    fun readRemoteAdConfig() {
        val temp = RemoteConfigUtils.getString(REMOTE_AD_CONFIG_KEY)
        if (temp.isNotBlank()){
            Log.e("readRemoteAdConfig", "readRemoteAdConfig: $temp")
        }
        val json = temp.ifBlank { DEFAULT_AD_CONFIG_JSON }
        applyAdConfigJson(json)
    }

    fun applyAdConfigJson(json: String) {
        AidAdHub.configure(parseCatalog(json, localCatalog))
    }

    fun parseCatalog(json: String, fallback: AdCatalog = localCatalog): AdCatalog {
        return runCatching {
            val root = JSONObject(json.ifBlank { DEFAULT_AD_CONFIG_JSON })
            val parsed = AdScene.entries.associateWith { scene ->
                parseItems(root, scene, fallback.unitsFor(scene))
            }
            AdCatalog.from(parsed)
        }.onFailure {
            AidAdHub.log("Ad config parse failed: ${it.message}")
        }.getOrDefault(fallback)
    }

    private fun parseItems(
        root: JSONObject,
        scene: AdScene,
        fallback: List<AdUnitConfig>
    ): List<AdUnitConfig> {
        val array = root.optJSONArray(scene.remoteKey) ?: return fallback
        val parsed = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                parseItem(scene, item)?.let { add(it) }
            }
        }
        return parsed.ifEmpty { fallback }
    }

    private fun parseItem(scene: AdScene, item: JSONObject): AdUnitConfig? {
        val platform = item.optString("ad_paltfrom")
            .ifBlank { item.optString("ad_platform") }
            .ifBlank { AdUnitConfig.ADMOB_PLATFORM }
            .trim()
        val format = AdFormat.fromRemote(item.optString("ad_type")) ?: scene.expectedFormat
        if (format != scene.expectedFormat) return null
        val unitId = item.optString("ad_unit_id").trim()
        if (unitId.isBlank()) return null
        return AdUnitConfig(
            scene = scene,
            unitId = unitId,
            platform = platform,
            format = format,
            ttlSeconds = item.optSeconds("ad_timelimit", AdUnitConfig.DEFAULT_TTL_SECONDS)
        )
    }

    private fun JSONObject.optSeconds(key: String, fallback: Int): Int {
        if (!has(key)) return fallback
        return when (val raw = opt(key)) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull() ?: fallback
            else -> fallback
        }.coerceAtLeast(1)
    }
}

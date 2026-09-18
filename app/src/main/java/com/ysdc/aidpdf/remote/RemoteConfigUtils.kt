package com.ysdc.aidpdf.remote

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.gson.Gson
import com.ysdc.aidpdf.BuildConfig
import com.ysdc.aidpdf.ad.AdsLimitManager
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.DEFAULT_ADS_LIMIT_CONFIG_JSON
import com.ysdc.aidpdf.ads.Ads
import com.ysdc.aidpdf.ads.config.AdsConfigBridge
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.reminder.config.DEFAULT_MEDIA_CONFIG_JSON
import com.ysdc.aidpdf.reminder.config.Media2Manager
import com.ysdc.aidpdf.reminder.config.MediaConfig
import com.ysdc.aidpdf.reminder.config.ReminderConfigRepository
import com.ysdc.aidpdf.reminder.config.ReminderOverlayConfigRepository
import com.ysdc.aidpdf.reminder.notice.PopRefresh
import com.ysdc.aidpdf.reminder.notice.ReminderNotificationCenter
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
import com.ysdc.aidpdf.utils.DEFAULT_PlAY_CONFIG_JSON
import com.ysdc.aidpdf.utils.InstallUtil
import org.json.JSONArray
import org.json.JSONObject

object RemoteConfigUtils {

    private const val TAG = "RemoteConfigUtils"
    private const val FETCH_INTERVAL_SECONDS = 3_600L
    private const val GLOBAL_BLOCK_SWITCH_KEY = "global_block_switch"
    private const val REFERRER_CONFIG_KEY = "gsbnnsk"
    private const val BLOCKED_REFERRER_KEY = "ac_black_refer_user"
    private const val ADB_BLOCK_SWITCH_KEY = "adb_block_switch"
    private const val REMOTE_AD_FUSE_CONFIG_KEY = "fuse_count"
    private const val REMOTE_MEDIA_CONFIG_KEY = "media_config"
    private const val REMOTE_ADS_LIMIT_CONFIG_KEY = "ads_limit"
    private const val REMOTE_GOOGLE_PLAY_BLOCK_KEY = "google_play_block"
    private const val DEFAULT_REFERRER_CONFIG = """
        {
          "active": 1,
          "content": [
            "fb4a",
            "instagram",
            "ig4a",
            "glid",
            "gclid",
            "not%20set",
            "youtubeads",
            "%7B%22",
            "adjust",
            "bytedance",
            "livead"
          ]
        }
    """
    private const val DEFAULT_BLOCKED_REFERRERS = "[\"gclid=123456789\"]"

    private const val POP_REFRESH = "pop_refresh"
    private const val DEFAULT_POP_REFRESH = """
        {
            "pop_refresh_switch":1,
            "times":30,
            "interval":2
        }
    """
    private const val DEFAULT_AC_NAT_CONFIG = """
        {
          "switch_open":1,
          "jump_percent":50
        }
        """

    private const val virtual_block_switch = "virtual_block_switch"
    private const val AC_NAT_CONFIG = "ac_nat_config"
    private const val REMOTE_AD_CONFIG_KEY = "ac_ad_config"
    private var initialized = false

    fun initRemoteConfig(application: Application, onApplied: () -> Unit = {}) {
        if (initialized) return
        initialized = true
        runCatching {
            FirebaseApp.initializeApp(application) ?: FirebaseApp.getInstance()
            val config = FirebaseRemoteConfig.getInstance()
            config.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(FETCH_INTERVAL_SECONDS)
                    .build()
            )
            config.setDefaultsAsync(
                buildMap {
                    putAll(
                        mapOf(
                            GLOBAL_BLOCK_SWITCH_KEY to "0",
                            REMOTE_ADS_LIMIT_CONFIG_KEY to DEFAULT_ADS_LIMIT_CONFIG_JSON,
                            REMOTE_MEDIA_CONFIG_KEY to DEFAULT_MEDIA_CONFIG_JSON,
                            REMOTE_GOOGLE_PLAY_BLOCK_KEY to DEFAULT_PlAY_CONFIG_JSON,
                            REFERRER_CONFIG_KEY to DEFAULT_REFERRER_CONFIG,
                            BLOCKED_REFERRER_KEY to DEFAULT_BLOCKED_REFERRERS,
                            ADB_BLOCK_SWITCH_KEY to "1",
                            POP_REFRESH to DEFAULT_POP_REFRESH,
                            AC_NAT_CONFIG to DEFAULT_AC_NAT_CONFIG,
                            REMOTE_AD_CONFIG_KEY to AdsConfigBridge.LOCAL_ADS_CONFIG_JSON
                        )
                    )
                    putAll(ReminderConfigRepository.defaultJsonValues())
                    putAll(ReminderOverlayConfigRepository.defaultJsonValues())
                }
            )
            getAllConfigs(onApplied)
            config.fetchAndActivate()
                .addOnSuccessListener {
                    Log.e(TAG, "initRemoteConfig: OnSuccessListener")
                    getAllConfigs(onApplied)
                }
                .addOnFailureListener { Log.e(TAG, "Remote Config fetch failed: ${it.message}") }
        }.onFailure {
            log("Firebase Remote Config unavailable: ${it.message}")
            getAllConfigs(onApplied)
        }
    }

    fun getString(key: String): String {
        if (!initialized) return ""
        return runCatching {
            FirebaseRemoteConfig.getInstance().getString(key).apply {
                Log.e(TAG, "getString: key = $key  value = $this")
            }
        }.getOrDefault("")
    }

    private fun getAllConfigs(onApplied: () -> Unit) {
        getBlockConfigs()
        getReminderConfigs()
        // 应用远程广告配置：从 Firebase 拉取 ac_ad_config 并热切换广告目录
        applyAdsRemoteConfig()
        runCatching(onApplied).onFailure { log("Config applied callback failed: ${it.message}") }
    }

    private fun getReminderConfigs() {
        val baseKey = if (ReminderConfigRepository.isSamsung) {
            ReminderConfigRepository.SAMSUNG_BASE_KEY
        } else {
            ReminderConfigRepository.STANDARD_BASE_KEY
        }
        val additionalKey = if (ReminderConfigRepository.isSamsung) {
            ReminderConfigRepository.SAMSUNG_ADDITIONAL_KEY
        } else {
            ReminderConfigRepository.STANDARD_ADDITIONAL_KEY
        }
        ReminderConfigRepository.applyRemote(
            baseJson = getString(baseKey),
            additionalJson = getString(additionalKey)
        )
        ReminderOverlayConfigRepository.applyRemote(
            getString(ReminderOverlayConfigRepository.REMOTE_KEY)
        )
        ReminderTriggerCenter.onConfigUpdated()
    }
    private fun applyAdsLimitConfig() {
        runCatching {
            val json = getString(REMOTE_ADS_LIMIT_CONFIG_KEY).ifBlank { DEFAULT_ADS_LIMIT_CONFIG_JSON }
            AdsLimitManager.applyConfig(json)
        }.onFailure {
            AdsLimitManager.applyConfig(DEFAULT_ADS_LIMIT_CONFIG_JSON)
            AidAdHub.log("Remote ads limit config skipped: ${it.message}")
        }
    }
    private fun applyGoogleConfig() {
        runCatching {
            val json = getString(REMOTE_GOOGLE_PLAY_BLOCK_KEY).ifBlank { DEFAULT_PlAY_CONFIG_JSON }
            InstallUtil.applyConfig(json)
        }.onFailure {
            InstallUtil.applyConfig(DEFAULT_PlAY_CONFIG_JSON)
            AidAdHub.log("Remote google play config skipped: ${it.message}")
        }
    }
    private fun applyMediaConfig() {
        runCatching {
            val json = getString(REMOTE_MEDIA_CONFIG_KEY).ifBlank { DEFAULT_MEDIA_CONFIG_JSON }
            Media2Manager.applyConfig(json)
        }.onFailure {
            Media2Manager.applyConfig(DEFAULT_MEDIA_CONFIG_JSON)
            AidAdHub.log("Remote notice refresh config skipped: ${it.message}")
        }
    }
    /**
     * 从 Firebase Remote Config 拉取远程广告配置，解析成功后热切换广告目录。
     * 失败时静默忽略，保持当前配置（首次启动为本地默认，后续为上次成功的远程配置）。
     */
    private fun applyAdsRemoteConfig() {
        runCatching {
            val catalog = AdsConfigBridge.remoteCatalog()
            if (catalog != null) {
                Ads.configure(catalog)
                Log.d(TAG, "远程广告配置已应用")
            }
        }.onFailure {
            log("Remote ad config apply failed: ${it.message}")
        }
    }
    private fun getBlockConfigs() {
        BlockUtils.applyGlobalSwitch(readSwitch(GLOBAL_BLOCK_SWITCH_KEY, defaultValue = false))
        BlockUtils.applyAdbSwitch(readSwitch(ADB_BLOCK_SWITCH_KEY, defaultValue = true))
        applyReferrerConfig()
        applyBlockedReferrers()
        applyPopRefresh()
        applyVirtualBlockSwitch()
        applyAdsLimitConfig()
        applyMediaConfig()
        applyGoogleConfig()
    }

    private fun applyReferrerConfig() {
        val raw = getString(REFERRER_CONFIG_KEY).ifBlank { DEFAULT_REFERRER_CONFIG }
        runCatching {
            val root = JSONObject(raw)
            val content = if (root.has("content")) {
                requireNotNull(root.optJSONArray("content")).toStringList()
            } else {
                JSONObject(DEFAULT_REFERRER_CONFIG).getJSONArray("content").toStringList()
            }
            BlockUtils.applyReferrerConfig(
                active = root.optInt("active", 1) == 1,
                content = content
            )
        }.onFailure {
            BlockUtils.restoreDefaultReferrerConfig()
            log("Referrer config failed: ${it.message}")
        }
    }

    private fun applyPopRefresh() {
        val raw = getString(POP_REFRESH).ifBlank { DEFAULT_POP_REFRESH }
        runCatching {
            ReminderNotificationCenter.popRefresh = Gson().fromJson(raw, PopRefresh::class.java)
        }.onFailure {
            ReminderNotificationCenter.popRefresh =
                Gson().fromJson(DEFAULT_POP_REFRESH, PopRefresh::class.java)
        }
    }
    private fun applyVirtualBlockSwitch() {
        if (readSwitch(virtual_block_switch, defaultValue = true)) {
            AdsConfigBridge.virtual_block_switch = 1
        } else {
            AdsConfigBridge.virtual_block_switch = 0
        }
    }
    private fun applyBlockedReferrers() {
        val raw = getString(BLOCKED_REFERRER_KEY).ifBlank { DEFAULT_BLOCKED_REFERRERS }
        runCatching {
            BlockUtils.applyBlockedReferrers(JSONArray(raw).toStringList())
        }.onFailure {
            BlockUtils.restoreDefaultBlockedReferrers()
            log("Blocked referrer config failed: ${it.message}")
        }
    }

    private fun readSwitch(key: String, defaultValue: Boolean): Boolean {
        val raw = getString(key).trim()
        if (raw.isBlank()) return defaultValue
        val number = raw.toIntOrNull() ?: runCatching {
            JSONObject(raw).optInt(key, if (defaultValue) 1 else 0)
        }.getOrDefault(if (defaultValue) 1 else 0)
        return number == 1
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}

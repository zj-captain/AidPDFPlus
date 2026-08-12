package com.ysdc.aidpdf.remote

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.gson.Gson
import com.ysdc.aidpdf.BuildConfig
import com.ysdc.aidpdf.ad.AdUnitFuseManager
import com.ysdc.aidpdf.ad.DEFAULT_AD_FUSE_CONFIG_JSON
import com.ysdc.aidpdf.ad.remote.AdRemoteBridge
import com.ysdc.aidpdf.ad.remote.NatConfig
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.reminder.config.ReminderConfigRepository
import com.ysdc.aidpdf.reminder.config.ReminderOverlayConfigRepository
import com.ysdc.aidpdf.reminder.notice.PopRefresh
import com.ysdc.aidpdf.reminder.notice.ReminderNotificationCenter
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
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
                            GLOBAL_BLOCK_SWITCH_KEY to "1",
                            REMOTE_AD_FUSE_CONFIG_KEY to DEFAULT_AD_FUSE_CONFIG_JSON,
                            REFERRER_CONFIG_KEY to DEFAULT_REFERRER_CONFIG,
                            BLOCKED_REFERRER_KEY to DEFAULT_BLOCKED_REFERRERS,
                            ADB_BLOCK_SWITCH_KEY to "1",
                            POP_REFRESH to DEFAULT_POP_REFRESH,
                            AC_NAT_CONFIG to DEFAULT_AC_NAT_CONFIG
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
        getAdConfig()
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
    private fun applyAdFuseConfig() {
        runCatching {
            val json = getString(REMOTE_AD_FUSE_CONFIG_KEY).ifBlank { DEFAULT_AD_FUSE_CONFIG_JSON }
            AdUnitFuseManager.applyConfig(json)
        }.onFailure {
            AdUnitFuseManager.applyConfig(DEFAULT_AD_FUSE_CONFIG_JSON)
        }
    }
    private fun getBlockConfigs() {
        BlockUtils.applyGlobalSwitch(readSwitch(GLOBAL_BLOCK_SWITCH_KEY, defaultValue = true))
        BlockUtils.applyAdbSwitch(readSwitch(ADB_BLOCK_SWITCH_KEY, defaultValue = true))
        applyReferrerConfig()
        applyBlockedReferrers()
        applyPopRefresh()
        applyVirtualBlockSwitch()
        applyAdFuseConfig()//熔断配置
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
            AdRemoteBridge.virtual_block_switch = 1
        } else {
            AdRemoteBridge.virtual_block_switch = 0
        }
    }

    private fun applyAcNatConfig(){
        val raw = getString(AC_NAT_CONFIG).ifBlank { DEFAULT_AC_NAT_CONFIG }
        runCatching {
            AdRemoteBridge.natConfig = Gson().fromJson(raw, NatConfig::class.java)
        }.onFailure {
            AdRemoteBridge.natConfig =
                Gson().fromJson(DEFAULT_AC_NAT_CONFIG, NatConfig::class.java)
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

    private fun getAdConfig() {
        runCatching { AdRemoteBridge.readRemoteAdConfig() }.onFailure { log("Remote ad config failed: ${it.message}") }
        runCatching { applyAcNatConfig() }
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}

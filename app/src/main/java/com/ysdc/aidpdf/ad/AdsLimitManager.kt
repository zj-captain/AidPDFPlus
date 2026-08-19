package com.ysdc.aidpdf.ad

import androidx.annotation.Keep
import com.google.gson.Gson
import com.ysdc.aidpdf.store.adsLimitStateJson
import java.util.Calendar

const val DEFAULT_ADS_LIMIT_CONFIG_JSON = """
{
  "ac_ads_limit": 30
}
"""

@Keep
data class AdsLimitConfig(
    // 每日单个用户展示的所有广告上限，单位/次。
    val ac_ads_limit: Int = 30,
)

@Keep
data class AdsLimitState(
    // 当天标识，格式为 yyyyMMdd。
    val dayKey: String = "",
    // 当天已展示广告次数。
    val shownCount: Int = 0,
)

object AdsLimitManager {

    private val gson = Gson()

    @Volatile
    var config: AdsLimitConfig = AdsLimitConfig()
        private set

    private var state: AdsLimitState = loadState()

    @Synchronized
    fun applyConfig(json: String) {
        config = runCatching {
            gson.fromJson(json.ifBlank { DEFAULT_ADS_LIMIT_CONFIG_JSON }, AdsLimitConfig::class.java)
        }.getOrElse {
            AdsLimitConfig()
        }
        state = normalizeState(state)
    }

    @Synchronized
    fun canShow(): Boolean {
        state = normalizeState(state)
        val limit = config.ac_ads_limit.coerceAtLeast(0)
        val canShow = state.shownCount < limit
        AidAdHub.log("广告展示上限检查: 今天已经展示 ${state.shownCount} 次，今日上限 $limit 次，canShow=$canShow")
        return canShow
    }

    @Synchronized
    fun getState(): AdsLimitState {
        state = normalizeState(state)
        return state
    }

    @Synchronized
    fun recordShow(): Boolean {
        state = normalizeState(state)
        val limit = config.ac_ads_limit.coerceAtLeast(0)
        if (state.shownCount >= limit) {
            AidAdHub.log("广告展示次数记录失败: 今天已经展示 ${state.shownCount} 次，已达到今日上限 $limit 次")
            return false
        }

        state = state.copy(shownCount = state.shownCount + 1)
        persistState()
        AidAdHub.log("广告展示次数已更新: 今天已经展示 ${state.shownCount} 次，今日上限 $limit 次")
        return true
    }

    @Synchronized
    private fun loadState(): AdsLimitState {
        val saved = runCatching {
            gson.fromJson(adsLimitStateJson, AdsLimitState::class.java)
        }.getOrNull() ?: AdsLimitState()
        return normalizeState(saved)
    }

    @Synchronized
    private fun normalizeState(source: AdsLimitState): AdsLimitState {
        val today = todayKey()
        val normalized = if (source.dayKey == today) {
            source
        } else {
            AdsLimitState(dayKey = today, shownCount = 0)
        }
        if (normalized != source) {
            state = normalized
            persistState()
        }
        return normalized
    }

    private fun persistState() {
        adsLimitStateJson = gson.toJson(state)
    }

    private fun todayKey(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format("%04d%02d%02d", year, month, day)
    }
}

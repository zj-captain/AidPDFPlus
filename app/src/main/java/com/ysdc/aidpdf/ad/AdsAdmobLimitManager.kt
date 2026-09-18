package com.ysdc.aidpdf.ad

import androidx.annotation.Keep
import com.google.gson.Gson
import com.ysdc.aidpdf.store.adsAdmobLimitStateJson
import com.ysdc.aidpdf.tracking.AidEventHub
import java.util.Calendar

const val DEFAULT_ADS_ADMOB_LIMIT_CONFIG_JSON = """
{
  "ac_admob_limit": 5
}
"""

@Keep
data class AdsAdmobLimitConfig(
    // 每日单个用户展示的所有广告上限，单位/次。
    val ac_admob_limit: Int = 30,
)

@Keep
data class AdsAdmobLimitState(
    // 当天标识，格式为 yyyyMMdd。
    val dayKey: String = "",
    // 当天已展示广告次数。
    val shownCount: Int = 0,
)

object AdsAdmobLimitManager {
    @Volatile
    private var isPost: Boolean = false
        private set
    private val gson = Gson()

    @Volatile
    var config: AdsAdmobLimitConfig = AdsAdmobLimitConfig()
        private set

    private var state: AdsAdmobLimitState = loadState()

    @Synchronized
    fun applyConfig(json: String) {
        config = runCatching {
            gson.fromJson(json.ifBlank { DEFAULT_ADS_ADMOB_LIMIT_CONFIG_JSON }, AdsAdmobLimitConfig::class.java)
        }.getOrElse {
            AdsAdmobLimitConfig()
        }
        state = normalizeState(state)
    }

    @Synchronized
    fun canShow(): Boolean {
        state = normalizeState(state)
        val limit = config.ac_admob_limit.coerceAtLeast(0)
        val canShow = state.shownCount < limit
        if (!canShow && !isPost){
            isPost = true
            AidEventHub.track("admobList")
        }
        AidAdHub.log("Admob广告展示上限检查: 今天已经展示 ${state.shownCount} 次，今日上限 $limit 次，canShow=$canShow")
        return canShow
    }

    @Synchronized
    fun getState(): AdsAdmobLimitState {
        state = normalizeState(state)
        return state
    }

    @Synchronized
    fun recordShow(): Boolean {
        state = normalizeState(state)
        val limit = config.ac_admob_limit.coerceAtLeast(0)
        if (state.shownCount >= limit) {
            AidAdHub.log("Admob广告展示次数记录失败: 今天已经展示 ${state.shownCount} 次，已达到今日上限 $limit 次")
            return false
        }

        state = state.copy(shownCount = state.shownCount + 1)
        persistState()
        AidAdHub.log("Admob广告展示次数已更新: 今天已经展示 ${state.shownCount} 次，今日上限 $limit 次")
        return true
    }

    @Synchronized
    private fun loadState(): AdsAdmobLimitState {
        val saved = runCatching {
            gson.fromJson(adsAdmobLimitStateJson, AdsAdmobLimitState::class.java)
        }.getOrNull() ?: AdsAdmobLimitState()
        return normalizeState(saved)
    }

    @Synchronized
    private fun normalizeState(source: AdsAdmobLimitState): AdsAdmobLimitState {
        val today = todayKey()
        val normalized = if (source.dayKey == today) {
            source
        } else {
            AdsAdmobLimitState(dayKey = today, shownCount = 0)
        }
        if (normalized != source) {
            state = normalized
            persistState()
        }
        return normalized
    }

    private fun persistState() {
        adsAdmobLimitStateJson = gson.toJson(state)
    }

    private fun todayKey(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format("%04d%02d%02d", year, month, day)
    }
}

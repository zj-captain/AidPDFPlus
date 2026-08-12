package com.ysdc.aidpdf.ad

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ysdc.aidpdf.store.adUnitFuseStatesJson

const val DEFAULT_AD_FUSE_CONFIG_JSON = """
{
  "cd_time": 12,
  "normal_fuse": 2,
  "perminent_fuse": 2
}
"""

@Keep
data class AdFuseConfig(
    // 普通熔断后的冷却时长，单位小时。
    val cd_time: Int = 12,
    // 首次进入冷却前允许出现的连续 no fill 次数。
    val normal_fuse: Int = 2,
    // 冷却恢复后再次触发永久熔断所需的连续 no fill 次数。
    val perminent_fuse: Int = 2,
)

@Keep
data class AdUnitFuseState(
    // 当前连续 no fill 次数；成功加载后会清零。
    val consecutiveNoFillCount: Int = 0,
    // 临时熔断截止时间；大于当前时间表示仍在冷却期。
    val cooldownUntilMillis: Long = 0L,
    // 是否已经经历过一次临时熔断，用于区分后续永久熔断阶段。
    val hasEnteredCooldownOnce: Boolean = false,
    // 是否已被当前设备永久熔断；一旦为 true，将永久跳过该广告源。
    val permanentlyFused: Boolean = false,
    // 是否已经上报过当前广告位永久熔断
    var upPostReport: Boolean = false,
)

object AdUnitFuseManager {

    private val gson = Gson()
    private val stateType = object : TypeToken<MutableMap<String, AdUnitFuseState>>() {}.type

    @Volatile
    var config: AdFuseConfig = AdFuseConfig()
        private set

    // 以 ad_unit_id 为键保存当前设备上的熔断状态，并持久化到 SharedPreferences。
    private val states: MutableMap<String, AdUnitFuseState> by lazy {
        runCatching {
            gson.fromJson<MutableMap<String, AdUnitFuseState>>(adUnitFuseStatesJson, stateType)
        }.getOrNull() ?: mutableMapOf()
    }

    @Synchronized
    fun applyConfig(json: String) {
        config = runCatching {
            gson.fromJson(json.ifBlank { DEFAULT_AD_FUSE_CONFIG_JSON }, AdFuseConfig::class.java)
        }.getOrElse {
            AdFuseConfig()
        }
    }

    @Synchronized
    fun canRequest(unitId: String): Boolean {
        if (unitId.isBlank()) return false

        val state = states[unitId] ?: return true
        if (state.permanentlyFused) return false
        if (state.cooldownUntilMillis <= 0L) return true
        if (System.currentTimeMillis() < state.cooldownUntilMillis) return false

        // 冷却期结束后自动恢复请求资格，但保留已经历过一次冷却的标记。
        states[unitId] = state.copy(
            consecutiveNoFillCount = 0,
            cooldownUntilMillis = 0L,
        )
        persistStates()
        return true
    }

    @Synchronized
    fun getState(unitId: String): AdUnitFuseState {
        return states[unitId] ?: AdUnitFuseState()
    }

    @Synchronized
    fun recordSuccess(unitId: String) {
        if (unitId.isBlank()) return

        val state = states[unitId] ?: return
        if (state.consecutiveNoFillCount == 0 && state.cooldownUntilMillis == 0L) return

        // 只要该广告源本次成功加载，就清空当前 no fill 连续计数和冷却时间。
        states[unitId] = state.copy(
            consecutiveNoFillCount = 0,
            cooldownUntilMillis = 0L,
        )
        persistStates()
    }

    @Synchronized
    fun recordNoFill(unitId: String) {
        if (unitId.isBlank()) return

        val currentState = states[unitId] ?: AdUnitFuseState()
        if (currentState.permanentlyFused) return
        if (currentState.cooldownUntilMillis > System.currentTimeMillis()) return

        val nextCount = currentState.consecutiveNoFillCount + 1
        val updatedState = when {
            !currentState.hasEnteredCooldownOnce && nextCount >= config.normal_fuse.coerceAtLeast(1) -> {
                // 首次达到普通熔断阈值，进入临时冷却期。
                currentState.copy(
                    consecutiveNoFillCount = 0,
                    cooldownUntilMillis = System.currentTimeMillis() + config.cd_time.coerceAtLeast(1) * 60L * 60L * 1000L,
                    hasEnteredCooldownOnce = true,
                )
            }

            currentState.hasEnteredCooldownOnce && nextCount >= config.perminent_fuse.coerceAtLeast(1) -> {
                // 冷却恢复后再次达到阈值，进入永久熔断。
                currentState.copy(
                    consecutiveNoFillCount = 0,
                    cooldownUntilMillis = 0L,
                    permanentlyFused = true,
                )
            }

            else -> {
                // 还未达到阈值，仅累加连续 no fill 次数。
                currentState.copy(consecutiveNoFillCount = nextCount)
            }
        }

        states[unitId] = updatedState
        persistStates()
    }

    @Synchronized
    private fun persistStates() {
        adUnitFuseStatesJson = gson.toJson(states)
    }
}

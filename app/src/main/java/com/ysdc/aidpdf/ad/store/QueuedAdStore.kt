package com.ysdc.aidpdf.ad.store

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.blankj.utilcode.util.NetworkUtils
import com.ysdc.aidpdf.ad.AdUnitFuseManager
import com.ysdc.aidpdf.ad.AdsLimitManager
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.config.AdUnitConfig
import com.ysdc.aidpdf.ad.core.AdLoadResult
import com.ysdc.aidpdf.ad.core.CachedAd
import com.ysdc.aidpdf.tracking.AidEventHub
import kotlin.compareTo

/**
 * 重试策略配置。
 *
 * @property delaysMs 重试延迟列表（毫秒），如 [1000, 2000, 4000] 表示失败后依次隔 1s、2s、4s 重试。
 *                    列表长度即最大重试次数，设为空列表则不做延迟重试，直接尝试下一个配置项。
 */
data class RetryPolicy(
    val delaysMs: List<Long> = listOf(1000L, 2000L, 4000L),
)
abstract class QueuedAdStore<T : CachedAd>(
    private val scene: AdScene
) {
    companion object {
        private const val ADMOB_NO_FILL_ERROR_CODE = 3
        private const val ADMOB_NO_FILL_ERROR_MSG = "No fill."
    }
    var onLoadComplete: ((Boolean) -> Unit)? = null

    private val candidates = mutableListOf<AdUnitConfig>()
    private val cachedAds = ArrayDeque<T>()
    private val loadCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var loading = false
    // ==================== 重试机制 ====================

    /** 重试策略，可在运行时按需调整 */
    @Volatile
    var retryPolicy: RetryPolicy = RetryPolicy()

    /** 延迟重试 Handler（主线程 Looper）。BaseAdSlot 实例由 AdCenter 单例持有，生命周期等同 Application，不会泄漏。 */
    private val retryHandler = Handler(Looper.getMainLooper())

    /** 当前配置项已重试次数，切换配置项时归零 */
    private var retryAttempt = 0
    fun configure(units: List<AdUnitConfig>) {
        cancelPendingRetries()
        candidates.clear()
        candidates.addAll(units)
        clear()
        retryAttempt = 0
    }

    fun load(context: Context) {
        if (!NetworkUtils.isConnected()) {
            AidAdHub.log("[广告位] ${scene.remoteKey} 当前网络连接异常，无法加载广告")
            return
        }
        if (!AdsLimitManager.canShow()) {
            return
        }
        if (loading || candidates.isEmpty()) return
        discardExpired()
        if (cachedAds.isNotEmpty()) return
        cancelPendingRetries()
        loading = true
        retryAttempt = 0
        loadCandidate(context, index = 0)
    }

    fun hasReady(): Boolean {
        discardExpired()
        return cachedAds.isNotEmpty()
    }

    fun isLoading(): Boolean = loading

    open fun waitUntilReady(context: Context, callback: (Boolean) -> Unit) {
        if (hasReady()) {
            callback(true)
            return
        }
        loadCallbacks.add(callback)
        load(context)
        if (!loading && candidates.isEmpty()) {
            notifyLoadCallbacks(false)
        }
    }

    fun waitCurrentLoad(callback: (Boolean) -> Unit) {
        if (!loading) {
            callback(hasReady())
            return
        }
        loadCallbacks.add(callback)
    }

    protected fun peek(): T? {
        discardExpired()
        return cachedAds.firstOrNull()
    }

    protected fun take(): T? {
        discardExpired()
        return cachedAds.removeFirstOrNull()
    }

    protected abstract fun createAd(unit: AdUnitConfig): T?

    fun clear() {
        cachedAds.forEach { it.release() }
        cachedAds.clear()
//        loading = false
        notifyLoadCallbacks(false)
    }

    private fun loadCandidate(context: Context, index: Int) {
        val unit = candidates.getOrNull(index)
        if (unit == null) {
            scheduleRetryOrComplete(context)
            return
        }
        if (!AdUnitFuseManager.canRequest(unit.unitId)) {
            val state = AdUnitFuseManager.getState(unit.unitId)
            val reason = if (state.permanentlyFused) {
                "已永久熔断"
            } else {
                "冷却中，截止时间=${state.cooldownUntilMillis}"
            }
            AidAdHub.log("${scene.remoteKey} 跳过广告源 ${unit.unitId}，原因：$reason")
            loadCandidate(context, index + 1)
            return
        }
        val ad = createAd(unit)
        if (ad == null) {
            loadCandidate(context, index + 1)
            return
        }

        ad.load(context) { result ->
            when (result) {
                AdLoadResult.Loaded -> {
                    cancelPendingRetries()
                    AdUnitFuseManager.recordSuccess(unit.unitId)
                    cachedAds.addLast(ad)
                    AidAdHub.log("${scene.remoteKey} loaded request=${ad.requestId}")
                    finishLoading(true)
                }
                is AdLoadResult.Failed -> {
                    AidAdHub.log("${scene.remoteKey} failed request=${ad.requestId}: ${result.reason}")
//                    if (result.errorCode == ADMOB_NO_FILL_ERROR_CODE && result.reason == ADMOB_NO_FILL_ERROR_MSG && scene.remoteKey != "ac_launch") {
//                        AdUnitFuseManager.recordNoFill(unit.unitId)
//                        val state = AdUnitFuseManager.getState(unit.unitId)
//                        val stateDesc = when {
//                            state.permanentlyFused -> {
//                                "永久熔断"
//                            }
//                            state.cooldownUntilMillis > System.currentTimeMillis() -> "进入冷却期，截止时间=${state.cooldownUntilMillis}"
//                            else -> "连续无填充次数=${state.consecutiveNoFillCount}"
//                        }
//                        //数据上报
//                        if (state.permanentlyFused) {
//                            AidEventHub.track("perminent_fuse", mapOf("id" to unit.unitId))
//                        }
//                        AidAdHub.log("${scene.remoteKey} 广告源 ${unit.unitId} 触发无填充统计，当前状态：$stateDesc")
//                    }
                    ad.release()
                    loadCandidate(context, index + 1)
                }
            }
        }
    }
    private fun scheduleRetryOrComplete(context: Context) {
        val delayMs = retryPolicy.delaysMs.getOrNull(retryAttempt)
        if (delayMs == null) {
            finishLoading(success = false)
            return
        }

        retryAttempt += 1
        AidAdHub.log("${scene.remoteKey} 所有候选广告源均请求失败，将在 ${delayMs}ms 后执行第 ${retryAttempt} 次退避重试")
        retryHandler.postDelayed(
            {
                if (!loading) return@postDelayed
                loadCandidate(context, index = 0)
            },
            delayMs
        )
    }
    private fun finishLoading(success: Boolean) {
        loading = false
        val ready = success && hasReady()
        onLoadComplete?.invoke(ready)
        notifyLoadCallbacks(ready)
    }

    private fun discardExpired() {
        if (cachedAds.isEmpty()) return
        val fresh = cachedAds.filter { ad ->
            val expired = ad.isExpired()
            if (expired) {
                ad.release()
                AidAdHub.log("${scene.remoteKey} expired request=${ad.requestId}")
            }
            !expired
        }
        cachedAds.clear()
        cachedAds.addAll(fresh)
    }

    private fun notifyLoadCallbacks(success: Boolean) {
        if (loadCallbacks.isEmpty()) return
        val callbacks = loadCallbacks.toList()
        loadCallbacks.clear()
        callbacks.forEach { callback -> callback(success) }
    }

    /** 取消所有待执行的重试任务，重置重试状态 */
    private fun cancelPendingRetries() {
        retryHandler.removeCallbacksAndMessages(null)
        retryAttempt = 0
    }
}

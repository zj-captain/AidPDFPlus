package com.ysdc.aidpdf.ad.store

import android.content.Context
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.config.AdUnitConfig
import com.ysdc.aidpdf.ad.core.AdLoadResult
import com.ysdc.aidpdf.ad.core.CachedAd

abstract class QueuedAdStore<T : CachedAd>(
    private val scene: AdScene
) {

    var onLoadComplete: ((Boolean) -> Unit)? = null

    private val candidates = mutableListOf<AdUnitConfig>()
    private val cachedAds = ArrayDeque<T>()
    private val loadCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var loading = false

    fun configure(units: List<AdUnitConfig>) {
        candidates.clear()
        candidates.addAll(units)
        clear()
    }

    fun load(context: Context) {
        if (loading || candidates.isEmpty()) return
        discardExpired()
        if (cachedAds.isNotEmpty()) return
        loading = true
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
        loading = false
        notifyLoadCallbacks(false)
    }

    private fun loadCandidate(context: Context, index: Int) {
        val unit = candidates.getOrNull(index)
        if (unit == null) {
            finishLoading(false)
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
                    cachedAds.addLast(ad)
                    AidAdHub.log("${scene.remoteKey} loaded request=${ad.requestId}")
                    finishLoading(true)
                }
                is AdLoadResult.Failed -> {
                    AidAdHub.log("${scene.remoteKey} failed request=${ad.requestId}: ${result.reason}")
                    ad.release()
                    loadCandidate(context, index + 1)
                }
            }
        }
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
}

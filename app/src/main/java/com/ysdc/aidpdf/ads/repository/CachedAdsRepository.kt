package com.ysdc.aidpdf.ads.repository

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.ysdc.aidpdf.ads.config.AdsCatalog
import com.ysdc.aidpdf.ads.config.AdsFormat
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.config.AdsUnitConfig
import com.ysdc.aidpdf.ads.core.AdsErrorCode
import com.ysdc.aidpdf.ads.core.AdsExceptionInfo
import com.ysdc.aidpdf.ads.core.AdsLogger
import com.ysdc.aidpdf.ads.core.AdsState
import com.ysdc.aidpdf.ads.core.AdsThread
import com.ysdc.aidpdf.ads.core.CachedAd
import com.ysdc.aidpdf.ads.core.RetryPolicy
import com.ysdc.aidpdf.ads.core.effectiveExpireAt
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.NativeRenderRequest
import com.ysdc.aidpdf.ads.provider.ProviderFactory
import com.ysdc.aidpdf.ads.runtime.ActiveDisplayRegistry
import com.ysdc.aidpdf.ads.runtime.PlatformRuntime
import com.ysdc.aidpdf.ads.runtime.RuntimeRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CachedAdsRepository(
    private val appContext: Context,
    private val runtimeRegistry: RuntimeRegistry = RuntimeRegistry(),
    private val displayRegistry: ActiveDisplayRegistry = ActiveDisplayRegistry()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var catalog: AdsCatalog = AdsCatalog()

    fun configure(newCatalog: AdsCatalog) {
        AdsThread.runOnMain {
            catalog = newCatalog
            runtimeRegistry.all().forEach { runtime ->
                val cache = runtime.cachedAd
                if (cache != null && !catalog.containsUnit(runtime.key.scene, runtime.key.platform, cache.config.unitId)) {
                    AdsLogger.w("场景=${runtime.key.scene} 平台=${runtime.key.platform} 的旧缓存广告位已不存在，立即销毁")
                    destroyCachedAd(runtime)
                }
            }
        }
    }

    fun load(scene: AdsScene, platform: AdsPlatform, activity: Activity? = null) {
        AdsThread.runOnMain {
            val runtime = runtimeRegistry.get(scene, platform)
            if (runtime.state == AdsState.Loading || runtime.state == AdsState.RetryWaiting) {
                AdsLogger.d("场景=$scene 平台=$platform 当前仍在加载链路中，忽略重复 load")
                return@runOnMain
            }
            if (platform == AdsPlatform.TradPlus && activity == null) {
                AdsLogger.w("场景=$scene 平台=$platform 加载失败：TradPlus 加载必须提供 Activity，本次不启动加载链路")
                return@runOnMain
            }
            val cache = runtime.cachedAd
            val now = System.currentTimeMillis()
            if (cache != null && !cache.isExpired(now)) {
                runtime.state = if (runtime.autoReloadEnabled) AdsState.Ready else AdsState.Suspended
                AdsLogger.d("场景=$scene 平台=$platform 已存在有效缓存，跳过加载")
                return@runOnMain
            }
            if (cache != null && cache.isExpired(now)) {
                AdsLogger.w("场景=$scene 平台=$platform 缓存已过期，加载前先销毁旧缓存")
                destroyCachedAd(runtime)
            }
            runtime.autoReloadEnabled = true
            runtime.retryJob?.cancel()
            runtime.retryJob = null
            runtime.retryStage = 0
            runtime.currentIndex = 0
            runtime.loadToken = System.nanoTime()
            runtime.state = AdsState.Loading
            tryLoad(runtime, activity)
        }
    }

    fun hasReady(scene: AdsScene, platform: AdsPlatform): Boolean {
        val runtime = runtimeRegistry.get(scene, platform)
        val cache = runtime.cachedAd ?: return false
        return !cache.isExpired(System.currentTimeMillis())
    }

    fun showFullScreen(
        scene: AdsScene,
        platform: AdsPlatform,
        activity: AppCompatActivity,
        onShown: () -> Unit,
        onClosed: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit
    ) {
        AdsThread.runOnMain {
            val runtime = runtimeRegistry.get(scene, platform)
            val cached = runtime.cachedAd
            if (cached == null) {
                onFailed(noCache(scene, platform))
                return@runOnMain
            }
            if (cached.isExpired(System.currentTimeMillis())) {
                AdsLogger.w("场景=$scene 平台=$platform 全屏缓存已过期，销毁并尝试补缓存")
                destroyCachedAd(runtime)
                if (runtime.autoReloadEnabled) {
                    load(scene, platform, activity = if (platform == AdsPlatform.TradPlus) activity else null)
                }
                onFailed(expired(scene, platform, cached.config.unitId))
                return@runOnMain
            }
            // 全屏广告在 show 前即消费，避免旧对象重复使用。
            runtime.cachedAd = null
            runtime.state = if (runtime.autoReloadEnabled) AdsState.Idle else AdsState.Suspended
            if (runtime.autoReloadEnabled) {
                load(scene, platform, activity = if (platform == AdsPlatform.TradPlus) activity else null)
            }
            ProviderFactory.cached(platform, scene.expectedFormat).showFullScreen(
                payload = cached.payload,
                activity = activity,
                onShown = onShown,
                onClosed = onClosed,
                onFailed = onFailed
            )
        }
    }

    fun showNative(
        scene: AdsScene,
        platform: AdsPlatform,
        activity: AppCompatActivity,
        parent: ViewGroup,
        request: NativeRenderRequest,
        onShown: () -> Unit,
        onImpression: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit
    ): AdDisplayHandle? {
        var handle: AdDisplayHandle? = null
        AdsThread.runOnMain {
            val runtime = runtimeRegistry.get(scene, platform)
            val cached = runtime.cachedAd ?: run {
                onFailed(noCache(scene, platform))
                return@runOnMain
            }
            if (cached.isExpired(System.currentTimeMillis())) {
                AdsLogger.w("场景=$scene 平台=$platform 原生缓存已过期，销毁并尝试补缓存")
                destroyCachedAd(runtime)
                if (runtime.autoReloadEnabled) {
                    load(scene, platform, activity = if (platform == AdsPlatform.TradPlus) activity else null)
                }
                onFailed(expired(scene, platform, cached.config.unitId))
                return@runOnMain
            }
            if (displayRegistry.hasNative(parent)) {
                val error = AdsExceptionInfo(
                    code = AdsErrorCode.ParentAlreadyOccupied,
                    scene = scene,
                    platform = platform,
                    message = "当前 parent 已存在未释放的 native handle，请先 destroy 再 show",
                    unitId = cached.config.unitId
                )
                AdsLogger.w(error.message)
                onFailed(error)
                return@runOnMain
            }
            val actualHandle = ProviderFactory.cached(platform, AdsFormat.Native).showNative(
                payload = cached.payload,
                activity = activity,
                parent = parent,
                request = request,
                onShown = {
                    // 原生广告只有在成功加入有效 parent 后才消费缓存，避免展示失败时误丢缓存。
                    runtime.cachedAd = null
                    runtime.state = if (runtime.autoReloadEnabled) AdsState.Idle else AdsState.Suspended
                    if (runtime.autoReloadEnabled) {
                        load(scene, platform, activity = if (platform == AdsPlatform.TradPlus) activity else null)
                    }
                    onShown()
                },
                onImpression = onImpression,
                onFailed = onFailed
            ) ?: return@runOnMain
            val wrappedHandle = object : AdDisplayHandle {
                private var destroyed = false

                override fun destroy() {
                    if (destroyed) return
                    destroyed = true
                    actualHandle.destroy()
                    displayRegistry.unregisterNative(parent, this)
                }
            }
            displayRegistry.registerNative(parent, wrappedHandle)
            handle = wrappedHandle
        }
        return handle
    }

    fun invalidate(scene: AdsScene, platform: AdsPlatform, activity: Activity? = null) {
        AdsThread.runOnMain {
            val runtime = runtimeRegistry.get(scene, platform)
            destroyCachedAd(runtime)
            runtime.autoReloadEnabled = true
            runtime.state = AdsState.Idle
            load(scene, platform, activity)
        }
    }

    fun destroyScene(scene: AdsScene, platform: AdsPlatform) {
        AdsThread.runOnMain {
            val runtime = runtimeRegistry.get(scene, platform)
            runtime.retryJob?.cancel()
            runtime.retryJob = null
            runtime.autoReloadEnabled = false
            runtime.state = AdsState.Suspended
            AdsLogger.d("场景=$scene 平台=$platform 已停止自动补缓存，当前缓存会保留")
        }
    }

    fun destroyAll() {
        AdsThread.runOnMain {
            runtimeRegistry.all().forEach { runtime ->
                runtime.retryJob?.cancel()
                runtime.retryJob = null
                destroyCachedAd(runtime)
                runtime.state = AdsState.Idle
                runtime.autoReloadEnabled = false
            }
            displayRegistry.snapshotHandles().forEach { it.destroy() }
            displayRegistry.clear()
        }
    }

    private fun tryLoad(runtime: PlatformRuntime, activity: Activity?) {
        val scene = runtime.key.scene
        val platform = runtime.key.platform
        val candidates = catalog.unitsFor(scene, platform)
        if (candidates.isEmpty()) {
            runtime.state = if (runtime.autoReloadEnabled) AdsState.Idle else AdsState.Suspended
            AdsLogger.w("场景=$scene 平台=$platform 当前没有可用广告配置，跳过加载")
            return
        }
        if (runtime.currentIndex >= candidates.size) {
            scheduleRetry(runtime, activity)
            return
        }
        val config = candidates[runtime.currentIndex]
        AdsLogger.d("场景=$scene 平台=$platform 开始尝试第${runtime.currentIndex + 1}个候选广告位=${config.unitId}")
        ProviderFactory.cached(platform, config.format).load(config, appContext, activity) { result ->
            AdsThread.runOnMain {
                result.onSuccess { payload ->
                    cacheLoadedAd(runtime, config, payload)
                }.onFailure { throwable ->
                    AdsLogger.w("场景=$scene 平台=$platform 广告加载失败，切换下一个候选位：${throwable.message}", throwable)
                    runtime.currentIndex += 1
                    tryLoad(runtime, activity)
                }
            }
        }
    }

    private fun cacheLoadedAd(runtime: PlatformRuntime, config: AdsUnitConfig, payload: Any) {
        destroyCachedAd(runtime)
        val loadedAt = System.currentTimeMillis()
        runtime.cachedAd = CachedAd(
            key = runtime.key,
            config = config,
            payload = payload,
            loadedAtMillis = loadedAt,
            expireAtMillis = effectiveExpireAt(
                scene = runtime.key.scene,
                platform = runtime.key.platform,
                format = config.format,
                loadedAtMillis = loadedAt,
                ttlSeconds = config.ttlSeconds
            )
        )
        runtime.state = if (runtime.autoReloadEnabled) AdsState.Ready else AdsState.Suspended
        runtime.retryStage = 0
        runtime.currentIndex = 0
        runtime.retryJob?.cancel()
        runtime.retryJob = null
        AdsLogger.d("场景=${runtime.key.scene} 平台=${runtime.key.platform} 缓存成功，广告位=${config.unitId}")
    }

    private fun scheduleRetry(runtime: PlatformRuntime, activity: Activity?) {
        if (!runtime.autoReloadEnabled) {
            runtime.state = AdsState.Suspended
            return
        }
        val delayMs = RetryPolicy.delayMillis(runtime.retryStage)
        runtime.state = AdsState.RetryWaiting
        runtime.retryJob?.cancel()
        runtime.retryJob = scope.launch {
            AdsLogger.w("场景=${runtime.key.scene} 平台=${runtime.key.platform} 本轮全部失败，${delayMs}ms 后开始下一轮重试")
            delay(delayMs)
            runtime.retryStage += 1
            runtime.currentIndex = 0
            runtime.state = AdsState.Loading
            tryLoad(runtime, activity)
        }
    }

    private fun destroyCachedAd(runtime: PlatformRuntime) {
        val cached = runtime.cachedAd ?: return
        ProviderFactory.cached(runtime.key.platform, cached.config.format).destroyPayload(cached.payload)
        runtime.cachedAd = null
    }

    private fun noCache(scene: AdsScene, platform: AdsPlatform): AdsExceptionInfo {
        return AdsExceptionInfo(
            code = AdsErrorCode.NoCache,
            scene = scene,
            platform = platform,
            message = "当前平台没有可展示的缓存广告"
        )
    }

    private fun expired(scene: AdsScene, platform: AdsPlatform, unitId: String): AdsExceptionInfo {
        return AdsExceptionInfo(
            code = AdsErrorCode.CacheExpired,
            scene = scene,
            platform = platform,
            message = "当前缓存广告已过期",
            unitId = unitId
        )
    }
}

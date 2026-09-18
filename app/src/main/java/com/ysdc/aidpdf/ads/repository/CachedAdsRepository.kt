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
import com.ysdc.aidpdf.ads.core.AdsEventTracker
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
import com.ysdc.aidpdf.ads.provider.admob.AdMobInterstitialPayload
import com.ysdc.aidpdf.ads.provider.admob.AdMobNativePayload
import com.ysdc.aidpdf.ads.provider.admob.AdMobOpenPayload
import com.ysdc.aidpdf.ads.provider.tradplus.TradPlusInterstitialPayload
import com.ysdc.aidpdf.ads.provider.tradplus.TradPlusNativePayload
import com.ysdc.aidpdf.ads.provider.tradplus.TradPlusOpenPayload
import com.ysdc.aidpdf.ads.runtime.ActiveDisplayRegistry
import com.ysdc.aidpdf.ads.runtime.PlatformRuntime
import com.ysdc.aidpdf.ads.runtime.RuntimeRegistry
import com.ysdc.aidpdf.ads.ui.FullScreenLoadingOverlay
import com.ysdc.aidpdf.ads.utils.AdMobPriceReflectionUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

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
        onFailed: (AdsExceptionInfo) -> Unit,
        trackingScene: String? = null,
        trackingType: String? = null,
        ecpm: Double? = null
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
            val loadingOverlay = FullScreenLoadingOverlay(activity)
            loadingOverlay.show()
            scope.launch {
                delay(500)
                AdsThread.runOnMain {
                    ProviderFactory.cached(platform, scene.expectedFormat).showFullScreen(
                        payload = cached.payload,
                        activity = activity,
                        onShown = {
                            loadingOverlay.dismiss()
                            onShown()
                        },
                        onClosed = {
                            loadingOverlay.dismiss()
                            onClosed()
                        },
                        onFailed = {
                            loadingOverlay.dismiss()
                            onFailed(it)
                        },
                        trackingScene = trackingScene,
                        trackingType = trackingType,
                        ecpm = ecpm
                    )
                }
            }
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
        onFailed: (AdsExceptionInfo) -> Unit,
        trackingScene: String? = null,
        ecpm: Double? = null
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
            var nativeShowFailedHandled = false
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
                onFailed = { error ->
                    // 原生广告一旦确认展示失败，需要及时淘汰当前缓存并触发补缓存，
                    // 避免下一次自动竞价继续反复命中同一条不可展示对象。
                    if (!nativeShowFailedHandled) {
                        nativeShowFailedHandled = true
                        if (runtime.cachedAd?.payload === cached.payload) {
                            AdsLogger.w("场景=$scene 平台=$platform 原生展示失败，淘汰当前缓存并尝试补缓存：${error.message}")
                            destroyCachedAd(runtime)
                            runtime.state = if (runtime.autoReloadEnabled) AdsState.Idle else AdsState.Suspended
                            if (runtime.autoReloadEnabled) {
                                load(scene, platform, activity = if (platform == AdsPlatform.TradPlus) activity else null)
                            }
                        }
                    }
                    onFailed(error)
                },
                trackingScene = trackingScene,
                ecpm = ecpm
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

    // ===== 自动竞价展示：业务层不再指定平台，展示前按价格比较选择价格更高的平台 =====

    /**
     * 全屏广告自动竞价展示入口。
     * 不修改缓存结构，只在展示时从 AdMob/TradPlus 现有缓存中取可展示候选，
     * 读取各自展示前 eCPM 后从高到低尝试；首选平台失败后自动降级到下一平台。
     */
    fun showBestFullScreen(
        scene: AdsScene,
        activity: AppCompatActivity,
        onShown: () -> Unit,
        onClosed: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit,
        trackingScene: String? = null,
        trackingType: String? = null
    ) {
        AdsThread.runOnMain {
            val ranked = rankCandidates(buildCandidates(scene,activity))
            if (ranked.isEmpty()) {
                onFailed(noCandidates(scene))
                return@runOnMain
            }
            AdsLogger.d("自动竞价：场景=$scene 候选顺序=${formatCandidateOrder(ranked)}")
            tryShowBestFullScreen(scene, ranked, 0, activity, onShown, onClosed, onFailed, null, trackingScene, trackingType)
        }
    }

    /**
     * 原生广告自动竞价展示入口。
     * parent 占用检查放在竞价之前：parent 已被占用时换平台也无法解决，直接失败不降级。
     */
    fun showBestNative(
        scene: AdsScene,
        activity: AppCompatActivity,
        parent: ViewGroup,
        request: NativeRenderRequest,
        onShown: () -> Unit,
        onImpression: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit,
        trackingScene: String? = null
    ): AdDisplayHandle? {
        var result: AdDisplayHandle? = null
        AdsThread.runOnMain {
            if (displayRegistry.hasNative(parent)) {
                val error = AdsExceptionInfo(
                    code = AdsErrorCode.ParentAlreadyOccupied,
                    scene = scene,
                    platform = AdsPlatform.TradPlus,
                    message = "当前 parent 已存在未释放的 native handle，请先 destroy 再 show"
                )
                AdsLogger.w(error.message)
                onFailed(error)
                return@runOnMain
            }
            val ranked = rankCandidates(buildCandidates(scene,activity))
            if (ranked.isEmpty()) {
                onFailed(noCandidates(scene))
                return@runOnMain
            }
            AdsLogger.d("自动竞价：场景=$scene 候选顺序=${formatCandidateOrder(ranked)}")
            var lastError: AdsExceptionInfo? = null
            for (candidate in ranked) {
                var failed = false
                val handle = showNative(
                    scene = scene,
                    platform = candidate.platform,
                    activity = activity,
                    parent = parent,
                    request = request,
                    onShown = onShown,
                    onImpression = onImpression,
                    onFailed = { error ->
                        if (!failed) {
                            failed = true
                            lastError = error
                            AdsLogger.w("自动竞价：场景=$scene 平台=${candidate.platform} 原生展示失败，尝试下一候选：${error.message}")
                        }
                    },
                    trackingScene = trackingScene,
                    ecpm = candidate.ecpm
                )
                if (handle != null) {
                    result = handle
                    return@runOnMain
                }
                if (!failed) {
                    lastError = AdsExceptionInfo(
                        code = AdsErrorCode.ShowFailed,
                        scene = scene,
                        platform = candidate.platform,
                        message = "原生广告未返回有效展示句柄"
                    )
                    AdsLogger.w("自动竞价：场景=$scene 平台=${candidate.platform} 原生未返回句柄，尝试下一候选")
                }
            }
            onFailed(lastError ?: noCandidates(scene))
        }
        return result
    }

    /** 全屏展示按候选顺序逐个尝试；只有当前平台失败才尝试下一平台，成功即结束。 */
    private fun tryShowBestFullScreen(
        scene: AdsScene,
        candidates: List<DisplayCandidate>,
        index: Int,
        activity: AppCompatActivity,
        onShown: () -> Unit,
        onClosed: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit,
        lastError: AdsExceptionInfo?,
        trackingScene: String?,
        trackingType: String?
    ) {
        if (index >= candidates.size) {
            onFailed(lastError ?: noCandidates(scene))
            return
        }
        val candidate = candidates[index]
        AdsLogger.d("自动竞价：场景=$scene 尝试展示第${index + 1}个候选 平台=${candidate.platform}")
        showFullScreen(
            scene = scene,
            platform = candidate.platform,
            activity = activity,
            onShown = onShown,
            onClosed = onClosed,
            onFailed = { error ->
                AdsLogger.w("自动竞价：场景=$scene 平台=${candidate.platform} 展示失败，尝试下一候选：${error.message}")
                tryShowBestFullScreen(scene, candidates, index + 1, activity, onShown, onClosed, onFailed, error, trackingScene, trackingType)
            },
            trackingScene = trackingScene,
            trackingType = trackingType,
            ecpm = candidate.ecpm
        )
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
            ProviderFactory.destroyAll()
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
        AdsEventTracker.reportLoadStarted(config)
        ProviderFactory.cached(platform, config.format).load(config, appContext, activity) { result ->
            AdsThread.runOnMain {
                result.onSuccess { payload ->
                    AdsEventTracker.reportLoaded(config, success = true, resultCode = 200, resultInfo = "")
                    cacheLoadedAd(runtime, config, payload)
                }.onFailure { throwable ->
                    AdsEventTracker.reportLoaded(config, success = false, resultCode = 0, resultInfo = throwable.message.orEmpty())
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
        if (runtime.retryStage > RetryPolicy.MAX_RETRY_STAGE) {
            // 业务明确要求重试只执行 1s / 2s / 4s 三次，最后一次结束后停止继续重试。
            runtime.retryJob?.cancel()
            runtime.retryJob = null
            runtime.state = AdsState.Idle
            AdsLogger.w("场景=${runtime.key.scene} 平台=${runtime.key.platform} 已完成 1s/2s/4s 全部重试，停止继续重试")
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

    // ===== 自动竞价辅助：候选构建、展示前取价、排序 =====

    /** 展示竞价候选：平台与展示前读取到的价格快照。 */
    private data class DisplayCandidate(
        val platform: AdsPlatform,
        val ecpm: Double?,
        val hasValidPrice: Boolean,
        val priceDebug: String
    )

    /** 平台固定兜底顺序：价格不可比较（同价/都无价）时 TradPlus 优先、AdMob 兜底。 */
    private fun platformOrder(platform: AdsPlatform): Int = when (platform) {
        AdsPlatform.TradPlus -> 0
        AdsPlatform.AdMob -> 1
    }

    private fun buildCandidates(scene: AdsScene,activity: AppCompatActivity,): List<DisplayCandidate> {
        return listOf(AdsPlatform.TradPlus, AdsPlatform.AdMob).mapNotNull { platform ->
            buildCandidate(scene,activity, platform)
        }
    }

    private fun buildCandidate(scene: AdsScene, activity: AppCompatActivity,platform: AdsPlatform): DisplayCandidate? {
        val runtime = runtimeRegistry.get(scene, platform)
        val cached = runtime.cachedAd ?: run {
            AdsLogger.d("自动竞价：场景=$scene 平台=$platform 无缓存，不参与竞价，尝试补缓存")
            maybeBackfillPlatform(scene,activity, platform)
            return null
        }
        if (cached.isExpired(System.currentTimeMillis())) {
            AdsLogger.w("自动竞价：场景=$scene 平台=$platform 缓存已过期，不参与竞价，尝试补缓存")
            destroyCachedAd(runtime)
            maybeBackfillPlatform(scene, activity,platform)
            return null
        }
        if (cached.config.format != scene.expectedFormat) {
            AdsLogger.w("自动竞价：场景=$scene 平台=$platform 格式不匹配（缓存=${cached.config.format} 期望=${scene.expectedFormat}），不参与竞价")
            return null
        }
        if (platform == AdsPlatform.TradPlus && !isTradPlusPayloadUsable(cached.payload)) {
            AdsLogger.w("自动竞价：场景=$scene 平台=$platform TradPlus 广告当前不可展示，不参与竞价")
            return null
        }
        val (rawEcpm, debug) = when (platform) {
            AdsPlatform.AdMob -> resolveAdMobEcpm(cached)
            AdsPlatform.TradPlus -> resolveTradPlusEcpm(cached)
        }
        val hasValid = rawEcpm != null && rawEcpm > 0.0 && !rawEcpm.isNaN() && !rawEcpm.isInfinite()
        return DisplayCandidate(
            platform = platform,
            ecpm = if (hasValid) rawEcpm else null,
            hasValidPrice = hasValid,
            priceDebug = debug
        )
    }

    private fun maybeBackfillPlatform(scene: AdsScene,activity: AppCompatActivity, platform: AdsPlatform) {
        val runtime = runtimeRegistry.get(scene, platform)
        if (!runtime.autoReloadEnabled) {
            AdsLogger.d("自动竞价：场景=$scene 平台=$platform 已关闭自动补缓存，跳过补缓存")
            return
        }
        if (runtime.state == AdsState.Loading || runtime.state == AdsState.RetryWaiting || runtime.state == AdsState.Suspended) {
            AdsLogger.d("自动竞价：场景=$scene 平台=$platform 当前状态=${runtime.state}，跳过补缓存")
            return
        }
        if (catalog.unitsFor(scene, platform).isEmpty()) {
            AdsLogger.d("自动竞价：场景=$scene 平台=$platform 没有广告配置，跳过补缓存")
            return
        }
        AdsLogger.d("自动竞价：场景=$scene 平台=$platform 满足补缓存条件，开始补缓存")
        load(scene, platform, activity = activity)
    }

    /**
     * TradPlus 可用性预检查：
     * - 开屏：沿用“有缓存即可尝试”的弱校验；
     * - 插屏：继续依赖 isReady，避免明显不可展示对象参与全屏竞价；
     * - 原生：只要缓存未过期就先允许进候选，最终能否展示交给 showNative() 再裁决。
     */
    private fun isTradPlusPayloadUsable(payload: Any): Boolean {
        return when (payload) {
            is TradPlusOpenPayload -> true
            is TradPlusInterstitialPayload -> payload.ad.isReady
            is TradPlusNativePayload -> true
            else -> false
        }
    }

    /** 读取 AdMob 展示前候选 eCPM；仅 PRIVATE_VALUE_UNVERIFIED 且 eCPM 为正时才视为有效价格。 */
    private fun resolveAdMobEcpm(cachedAd: CachedAd): Pair<Double?, String> {
        val ad: Any = when (val payload = cachedAd.payload) {
            is AdMobOpenPayload -> payload.ad
            is AdMobInterstitialPayload -> payload.ad
            is AdMobNativePayload -> payload.ad
            else -> return null to "payload 类型不是 AdMob 广告对象"
        }
        val testEcpm = Random.nextInt(1, 6) / 100.0
        return testEcpm  to "临时测试价 eCPM=$testEcpm"
//        return try {
//            val result = AdMobPriceReflectionUtil.probe(ad, cachedAd.config.unitId)
//            if (result.status == AdMobPriceReflectionUtil.Status.PRIVATE_VALUE_UNVERIFIED && result.price != null) {
//                result.price.ecpm to "status=${result.status} path=${result.matchedPath}"
//            } else {
//                null to "status=${result.status} 未取得展示前候选价"
//            }
//        } catch (throwable: Throwable) {
//            null to "probe 异常：${throwable.javaClass.simpleName}"
//        }
    }

    /** 读取 TradPlus 展示前 eCPM；按约定所有广告源统一走 recursiveComparePrice。 */
    private fun resolveTradPlusEcpm(cachedAd: CachedAd): Pair<Double?, String> {
        return try {
//            val ecpm = ComparePriceUtil.recursiveComparePrice(cachedAd.config.unitId)
//            ecpm to "recursiveComparePrice 返回 eCPM=$ecpm"

            val testEcpm = Random.nextInt(4, 6) / 100.0
            return testEcpm  to "临时测试价 eCPM=$testEcpm"
        } catch (throwable: Throwable) {
            null to "recursiveComparePrice 异常：${throwable.javaClass.simpleName}"
        }
    }

    /** 排序：有效价格优先；同为有效价按 eCPM 降序；同价或无价按 TradPlus 优先兜底。 */
    private fun rankCandidates(candidates: List<DisplayCandidate>): List<DisplayCandidate> {
        return candidates.sortedWith(
            compareByDescending<DisplayCandidate> { it.hasValidPrice }
                .thenByDescending { it.ecpm ?: Double.NEGATIVE_INFINITY }
                .thenBy { platformOrder(it.platform) }
        )
    }

    private fun formatCandidateOrder(candidates: List<DisplayCandidate>): String {
        return candidates.joinToString(" -> ") { "${it.platform}(eCPM=${it.ecpm ?: "无价"}, ${it.priceDebug})" }
    }

    /** 两个平台都无可展示缓存时的统一错误。 */
    private fun noCandidates(scene: AdsScene): AdsExceptionInfo {
        return AdsExceptionInfo(
            code = AdsErrorCode.NoCache,
            scene = scene,
            platform = AdsPlatform.TradPlus,
            message = "AdMob 与 TradPlus 均无可展示缓存"
        )
    }
}

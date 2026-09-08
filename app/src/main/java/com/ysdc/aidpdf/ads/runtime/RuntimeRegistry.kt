package com.ysdc.aidpdf.ads.runtime

import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.core.ScenePlatformKey

class RuntimeRegistry {
    private val runtimes = mutableMapOf<ScenePlatformKey, PlatformRuntime>()

    fun get(scene: AdsScene, platform: AdsPlatform): PlatformRuntime {
        val key = ScenePlatformKey(scene, platform)
        return runtimes.getOrPut(key) { PlatformRuntime(key) }
    }

    fun all(): Collection<PlatformRuntime> = runtimes.values
}

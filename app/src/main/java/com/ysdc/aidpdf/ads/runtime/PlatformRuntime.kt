package com.ysdc.aidpdf.ads.runtime

import com.ysdc.aidpdf.ads.core.AdsState
import com.ysdc.aidpdf.ads.core.CachedAd
import com.ysdc.aidpdf.ads.core.ScenePlatformKey
import kotlinx.coroutines.Job

data class PlatformRuntime(
    val key: ScenePlatformKey,
    var state: AdsState = AdsState.Idle,
    var cachedAd: CachedAd? = null,
    var currentIndex: Int = 0,
    var retryStage: Int = 0,
    var retryJob: Job? = null,
    var loadToken: Long = 0L,
    var autoReloadEnabled: Boolean = true
)

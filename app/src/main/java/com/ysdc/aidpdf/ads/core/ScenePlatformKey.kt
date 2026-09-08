package com.ysdc.aidpdf.ads.core

import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsScene

data class ScenePlatformKey(
    val scene: AdsScene,
    val platform: AdsPlatform
)

package com.ysdc.aidpdf.ads.core

import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsScene

data class AdsExceptionInfo(
    val code: AdsErrorCode,
    val scene: AdsScene,
    val platform: AdsPlatform,
    val message: String,
    val unitId: String? = null
)

package com.ysdc.aidpdf.ad.config

import androidx.annotation.Keep

@Keep
data class AdUnitConfig(
    val scene: AdScene,
    val unitId: String,
    val platform: String,
    val format: AdFormat,
    val ttlSeconds: Int
) {

    val isAdMob: Boolean
        get() = platform.equals(ADMOB_PLATFORM, ignoreCase = true)

    companion object {
        const val ADMOB_PLATFORM = "admob"
        const val DEFAULT_TTL_SECONDS = 1200
    }
}


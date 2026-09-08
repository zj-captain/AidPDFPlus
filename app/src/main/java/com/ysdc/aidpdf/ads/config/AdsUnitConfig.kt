package com.ysdc.aidpdf.ads.config

data class AdsUnitConfig(
    val scene: AdsScene,
    val unitId: String,
    val platform: AdsPlatform,
    val format: AdsFormat,
    val ttlSeconds: Int
)

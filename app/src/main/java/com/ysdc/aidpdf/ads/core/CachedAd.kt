package com.ysdc.aidpdf.ads.core

import com.ysdc.aidpdf.ads.config.AdsFormat
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.config.AdsUnitConfig

data class CachedAd(
    val key: ScenePlatformKey,
    val config: AdsUnitConfig,
    val payload: Any,
    val loadedAtMillis: Long,
    val expireAtMillis: Long
) {
    fun isExpired(now: Long): Boolean = now >= expireAtMillis
}

fun effectiveExpireAt(
    scene: AdsScene,
    platform: AdsPlatform,
    format: AdsFormat,
    loadedAtMillis: Long,
    ttlSeconds: Int
): Long {
    val ttlMillis = ttlSeconds * 1000L
    val rawExpireAt = loadedAtMillis + ttlMillis
    // 开屏广告对 AdMob 追加 4 小时上限保护，避免业务配置超过 SDK 有效期。
    return if (scene == AdsScene.Launch && platform == AdsPlatform.AdMob && format == AdsFormat.Open) {
        minOf(rawExpireAt, loadedAtMillis + 4 * 60 * 60 * 1000L)
    } else {
        rawExpireAt
    }
}

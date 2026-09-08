package com.ysdc.aidpdf.ads.provider.tradplus

import android.app.Activity
import com.tradplus.ads.open.interstitial.TPInterstitial
import com.tradplus.ads.open.nativead.TPNative
import com.tradplus.ads.open.splash.TPSplash
import com.ysdc.aidpdf.ads.config.AdsFormat

/**
 * 按「广告格式 + 广告位 ID」复用 TradPlus 广告对象，避免每次加载都重复 new 实例。
 */
object TradPlusHolderRegistry {
    private val splashMap = mutableMapOf<String, TPSplash>()
    private val interstitialMap = mutableMapOf<String, TPInterstitial>()
    private val nativeMap = mutableMapOf<String, TPNative>()

    fun getOrCreateSplash(activity: Activity, unitId: String): TPSplash {
        return splashMap.getOrPut(unitId) { TPSplash(activity, unitId) }
    }

    fun getOrCreateInterstitial(activity: Activity, unitId: String): TPInterstitial {
        return interstitialMap.getOrPut(unitId) { TPInterstitial(activity, unitId) }
    }

    fun getOrCreateNative(activity: Activity, unitId: String): TPNative {
        return nativeMap.getOrPut(unitId) { TPNative(activity, unitId) }
    }

    fun destroy(unitId: String, format: AdsFormat) {
        when (format) {
            AdsFormat.Open -> splashMap.remove(unitId)?.onDestroy()
            AdsFormat.Interstitial -> interstitialMap.remove(unitId)?.onDestroy()
            AdsFormat.Native -> nativeMap.remove(unitId)?.onDestroy()
            AdsFormat.Banner -> Unit
        }
    }

    fun destroyAll() {
        splashMap.values.forEach { it.onDestroy() }
        interstitialMap.values.forEach { it.onDestroy() }
        nativeMap.values.forEach { it.onDestroy() }
        splashMap.clear()
        interstitialMap.clear()
        nativeMap.clear()
    }
}

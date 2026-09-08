package com.ysdc.aidpdf.ads.provider

import com.ysdc.aidpdf.ads.config.AdsFormat
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.provider.admob.AdMobBannerProvider
import com.ysdc.aidpdf.ads.provider.admob.AdMobCachedAdProvider
import com.ysdc.aidpdf.ads.provider.tradplus.TradPlusBannerProvider
import com.ysdc.aidpdf.ads.provider.tradplus.TradPlusCachedAdProvider
import com.ysdc.aidpdf.ads.provider.tradplus.TradPlusHolderRegistry

object ProviderFactory {
    private val admobCachedProvider = AdMobCachedAdProvider()
    private val tradPlusCachedProvider = TradPlusCachedAdProvider()
    private val admobBannerProvider = AdMobBannerProvider()
    private val tradPlusBannerProvider = TradPlusBannerProvider()

    fun cached(platform: AdsPlatform, format: AdsFormat): CachedAdProvider {
        return when (platform) {
            AdsPlatform.AdMob -> admobCachedProvider
            AdsPlatform.TradPlus -> tradPlusCachedProvider
        }
    }

    fun banner(platform: AdsPlatform): BannerAdProvider {
        return when (platform) {
            AdsPlatform.AdMob -> admobBannerProvider
            AdsPlatform.TradPlus -> tradPlusBannerProvider
        }
    }

    fun destroyAll() {
        // 当前只有 TradPlus 维护了内部广告对象复用池，需要在全局销毁时一并清理，避免旧对象残留。
        TradPlusHolderRegistry.destroyAll()
    }
}

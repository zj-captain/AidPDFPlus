package com.ysdc.aidpdf.ads.provider

import com.ysdc.aidpdf.ads.config.AdsFormat
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.provider.admob.AdMobBannerProvider
import com.ysdc.aidpdf.ads.provider.admob.AdMobCachedAdProvider
import com.ysdc.aidpdf.ads.provider.tradplus.TradPlusBannerProvider
import com.ysdc.aidpdf.ads.provider.tradplus.TradPlusCachedAdProvider

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
}

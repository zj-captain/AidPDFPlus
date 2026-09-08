package com.ysdc.aidpdf.ads.provider.tradplus

import com.tradplus.ads.open.banner.TPBanner
import com.tradplus.ads.open.interstitial.TPInterstitial
import com.tradplus.ads.open.nativead.TPNative
import com.tradplus.ads.open.splash.TPSplash
import com.ysdc.aidpdf.ads.config.AdsUnitConfig

data class TradPlusOpenPayload(
    val config: AdsUnitConfig,
    val ad: TPSplash
)

data class TradPlusInterstitialPayload(
    val config: AdsUnitConfig,
    val ad: TPInterstitial
)

data class TradPlusNativePayload(
    val config: AdsUnitConfig,
    val ad: TPNative
)

data class TradPlusBannerPayload(
    val config: AdsUnitConfig,
    val ad: TPBanner
)

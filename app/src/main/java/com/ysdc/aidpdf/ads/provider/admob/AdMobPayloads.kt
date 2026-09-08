package com.ysdc.aidpdf.ads.provider.admob

import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.ysdc.aidpdf.ads.config.AdsUnitConfig

data class AdMobOpenPayload(
    val config: AdsUnitConfig,
    val ad: AppOpenAd
)

data class AdMobInterstitialPayload(
    val config: AdsUnitConfig,
    val ad: InterstitialAd
)

data class AdMobNativePayload(
    val config: AdsUnitConfig,
    val ad: NativeAd
)

data class AdMobBannerPayload(
    val config: AdsUnitConfig,
    val adView: AdView
)

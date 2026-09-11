package com.ysdc.aidpdf.ads.config

enum class AdsScene(
    val remoteKey: String,
    val expectedFormat: AdsFormat
) {
    Launch("ac_launch", AdsFormat.Open),
    BackInterstitial("ac_back_int", AdsFormat.Interstitial),
    ResultInterstitial("ac_result_int", AdsFormat.Interstitial),
    MainNative("ac_main_nat", AdsFormat.Native),
    ResultNative("ac_result_nat", AdsFormat.Native),
    MainBanner("ac_main_banner", AdsFormat.Banner)
}

package com.ysdc.aidpdf.ad.config

enum class AdScene(
    val remoteKey: String,
    val expectedFormat: AdFormat
) {
    Launch("ac_launch", AdFormat.Open),
    BottomInterstitial("ac_bottom_int", AdFormat.Interstitial),
    TopInterstitial("ac_top_int", AdFormat.Interstitial),
    CheckInterstitial("ac_check_int", AdFormat.Interstitial),
    MainBackInterstitial("ac_mainback_int", AdFormat.Interstitial),
    MainNative("ac_main_nat", AdFormat.Native),
    MainBanner("ac_main_banner", AdFormat.Banner),
    UninstallFirstNative("ac_uninstall1_nat", AdFormat.Native),
    UninstallFirstInterstitial("ac_uninstall1_int", AdFormat.Interstitial),
    UninstallSecondNative("ac_uninstall2_nat", AdFormat.Native),
    UninstallSecondInterstitial("ac_uninstall2_int", AdFormat.Interstitial),
    ResultNative("ac_result_nat", AdFormat.Native);

    val isFullScreen: Boolean
        get() = expectedFormat == AdFormat.Open || expectedFormat == AdFormat.Interstitial

    val isNative: Boolean
        get() = expectedFormat == AdFormat.Native

    val isBanner: Boolean
        get() = expectedFormat == AdFormat.Banner

    val trackingKey: String
        get() = when (this) {
            Launch -> AdTrackingScene.LAUNCH_OPEN
            MainBackInterstitial -> AdTrackingScene.BACK_INTERSTITIAL
            else -> remoteKey
        }

    companion object {
        val fullScreenScenes: List<AdScene> = entries.filter { it.isFullScreen }
        val nativeScenes: List<AdScene> = entries.filter { it.isNative }
        val bannerScenes: List<AdScene> = entries.filter { it.isBanner }
    }
}

object AdTrackingScene {
    const val LAUNCH_OPEN = "ac_launch_open"
    const val LAUNCH_NOTIFICATION = "ac_launch_notification"
    const val LAUNCH_POPUP = "ac_launch_popup"
    const val LAUNCH_MEDIA = "ac_launch_media"
    const val LAUNCH_UNINSTALL = "ac_launch_uninstall"
    const val LAUNCH_BAR = "ac_launch_bar"
    const val LANGUAGE_INTERSTITIAL = "ac_language_int"
    const val GUIDE_INTERSTITIAL = "ac_guide_int"
    const val SCAN_INTERSTITIAL = "ac_scan_int"
    const val BACK_INTERSTITIAL = "ac_back_int"
    const val LANGUAGE_NATIVE = "ac_language_nat"
    const val GUIDE_NATIVE = "ac_guide_nat"
    const val SCAN_NATIVE = "ac_scan_nat"
}

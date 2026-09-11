package com.ysdc.aidpdf.ads.provider.admob

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsUnitConfig
import com.ysdc.aidpdf.ads.core.AdsErrorCode
import com.ysdc.aidpdf.ads.core.AdsEventTracker
import com.ysdc.aidpdf.ads.core.AdsExceptionInfo
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.BannerRenderRequest
import com.ysdc.aidpdf.ads.provider.BannerAdProvider

class AdMobBannerProvider : BannerAdProvider {
    override fun supports(platform: AdsPlatform): Boolean = platform == AdsPlatform.AdMob

    override fun showBanner(
        config: AdsUnitConfig,
        activity: AppCompatActivity,
        parent: ViewGroup,
        request: BannerRenderRequest,
        onImpression: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit,
        trackingScene: String?
    ): AdDisplayHandle? {
        val adView = AdView(activity)
        val adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, activity.resources.displayMetrics.widthPixels)
        val adRequest = BannerAdRequest.Builder(config.unitId, adSize).build()
        AdsEventTracker.reportLoadStarted(config)
        adView.loadAd(adRequest, object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(ad: BannerAd) {
                if (adView.parent == null) {
                    parent.addView(adView)
                }
                AdsEventTracker.reportLoaded(config, success = true, resultCode = 200, resultInfo = "")
                AdsEventTracker.reportShown(config, trackingScene)
//                AdEventTracker.reportPaidValue(trackingScene?:config.scene.remoteKey, config, value, ad.getResponseInfo())
//                AdEventTracker.reportTotalAdsRenenue001Admob(value)
                onImpression()
            }
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                AdsEventTracker.reportLoaded(config, success = false, resultCode = 0, resultInfo = loadAdError.message)
                val error = AdsExceptionInfo(
                    code = AdsErrorCode.LoadFailed,
                    scene = config.scene,
                    platform = AdsPlatform.AdMob,
                    message = loadAdError.message,
                    unitId = config.unitId
                )
                onFailed(error)
            }
        })
        return AdMobDisplayHandle(parent = parent, child = adView, adView = adView)
    }
}

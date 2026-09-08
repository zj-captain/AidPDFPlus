package com.ysdc.aidpdf.ads.provider.admob

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsUnitConfig
import com.ysdc.aidpdf.ads.core.AdsErrorCode
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
        onFailed: (AdsExceptionInfo) -> Unit
    ): AdDisplayHandle? {
        val adView = AdView(activity)
        val adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, activity.resources.displayMetrics.widthPixels)
        val adRequest = BannerAdRequest.Builder(config.unitId, adSize).build()
        adView.loadAd(adRequest, object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(ad: BannerAd) {
                if (adView.parent == null) {
                    parent.addView(adView)
                }
                onImpression()
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                onFailed(
                    AdsExceptionInfo(
                        code = AdsErrorCode.LoadFailed,
                        scene = config.scene,
                        platform = AdsPlatform.AdMob,
                        message = loadAdError.message,
                        unitId = config.unitId
                    )
                )
            }
        })
        return AdMobDisplayHandle(parent = parent, child = adView, adView = adView)
    }
}

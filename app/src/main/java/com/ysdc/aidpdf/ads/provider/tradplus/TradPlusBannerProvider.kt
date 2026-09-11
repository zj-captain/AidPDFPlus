package com.ysdc.aidpdf.ads.provider.tradplus

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.tradplus.ads.base.bean.TPAdError
import com.tradplus.ads.base.bean.TPAdInfo
import com.tradplus.ads.open.banner.BannerAdListener
import com.tradplus.ads.open.banner.TPBanner
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsUnitConfig
import com.ysdc.aidpdf.ads.core.AdsErrorCode
import com.ysdc.aidpdf.ads.core.AdsEventTracker
import com.ysdc.aidpdf.ads.core.AdsExceptionInfo
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.BannerRenderRequest
import com.ysdc.aidpdf.ads.provider.BannerAdProvider

class TradPlusBannerProvider : BannerAdProvider {
    override fun supports(platform: AdsPlatform): Boolean = platform == AdsPlatform.TradPlus

    override fun showBanner(
        config: AdsUnitConfig,
        activity: AppCompatActivity,
        parent: ViewGroup,
        request: BannerRenderRequest,
        onImpression: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit,
        trackingScene: String?
    ): AdDisplayHandle? {
        val banner = TPBanner(activity)
        // 这里关闭自动展示后，由页面容器自己控制挂载时机，避免 Banner 在错误时机自动弹出。
        banner.closeAutoShow()
        AdsEventTracker.reportLoadStarted(config)
        banner.setAdListener(object : BannerAdListener() {
            override fun onAdClicked(tpAdInfo: TPAdInfo?) {
                AdsEventTracker.reportClick(config, trackingScene)
            }

            override fun onAdImpression(tpAdInfo: TPAdInfo?) {
                AdsEventTracker.reportShown(config, trackingScene, ecpm = tpAdInfo?.ecpm?.toDoubleOrNull()?:0.00, reEcpm = tpAdInfo?.ecpm?.toDoubleOrNull()?:0.00)
                AdEventTracker.sendTpRevenue(tpAdInfo?.ecpm?.toDoubleOrNull()?:0.00,tpAdInfo?.ecpmcny,tpAdInfo?.adSourceName)
                AdEventTracker.reportTotalAdsRenenue001Tp(tpAdInfo?.ecpm?.toDoubleOrNull()?:0.00)
                onImpression()
            }

            override fun onAdLoaded(tpAdInfo: TPAdInfo?) {
                AdsEventTracker.reportLoaded(config, success = true, resultCode = 200, resultInfo = "")
            }

            override fun onAdLoadFailed(error: TPAdError?) {
                AdsEventTracker.reportLoaded(config, success = false, resultCode = 0, resultInfo = error?.errorMsg.orEmpty())
                val exceptionInfo = AdsExceptionInfo(
                    code = AdsErrorCode.LoadFailed,
                    scene = config.scene,
                    platform = AdsPlatform.TradPlus,
                    // Banner 回调同样可能给空错误对象，这里补默认值避免日志丢失关键信息。
                    message = error?.errorMsg ?: "TradPlus banner load failed",
                    unitId = config.unitId
                )
                onFailed(exceptionInfo)
            }

            override fun onAdClosed(tpAdInfo: TPAdInfo?) = Unit
        })
        parent.addView(banner)
        banner.loadAd(config.unitId)
        return TradPlusDisplayHandle(parent = parent, child = banner, tpBanner = banner)
    }
}

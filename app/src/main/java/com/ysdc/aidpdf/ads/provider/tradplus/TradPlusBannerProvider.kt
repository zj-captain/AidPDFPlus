package com.ysdc.aidpdf.ads.provider.tradplus

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.tradplus.ads.base.bean.TPAdError
import com.tradplus.ads.base.bean.TPAdInfo
import com.tradplus.ads.open.banner.BannerAdListener
import com.tradplus.ads.open.banner.TPBanner
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsUnitConfig
import com.ysdc.aidpdf.ads.core.AdsErrorCode
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
        onFailed: (AdsExceptionInfo) -> Unit
    ): AdDisplayHandle? {
        val banner = TPBanner(activity)
        // 这里关闭自动展示后，由页面容器自己控制挂载时机，避免 Banner 在错误时机自动弹出。
        banner.closeAutoShow()
        banner.setAdListener(object : BannerAdListener() {
            override fun onAdClicked(tpAdInfo: TPAdInfo) = Unit

            override fun onAdImpression(tpAdInfo: TPAdInfo) {
                onImpression()
            }

            override fun onAdLoaded(tpAdInfo: TPAdInfo) = Unit

            override fun onAdLoadFailed(error: TPAdError) {
                onFailed(
                    AdsExceptionInfo(
                        code = AdsErrorCode.LoadFailed,
                        scene = config.scene,
                        platform = AdsPlatform.TradPlus,
                        message = error.errorMsg,
                        unitId = config.unitId
                    )
                )
            }

            override fun onAdClosed(tpAdInfo: TPAdInfo) = Unit
        })
        parent.addView(banner)
        banner.loadAd(config.unitId)
        return TradPlusDisplayHandle(parent = parent, child = banner, tpBanner = banner)
    }
}

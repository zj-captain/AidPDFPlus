package com.ysdc.aidpdf.ads.provider.admob

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ads.config.AdsConfigBridge
import com.ysdc.aidpdf.ads.model.NativeAdStyle
import com.ysdc.aidpdf.core.block.BlockUtils
import kotlin.random.Random

object AdMobNativeRenderer {

    fun createAndBind(parent: ViewGroup, payload: AdMobNativePayload, style: NativeAdStyle): NativeAdView {
        val layoutId = when (style) {
            NativeAdStyle.Large -> R.layout.layout_ad_native_large
            NativeAdStyle.Medium -> R.layout.layout_ad_native_medium
            NativeAdStyle.Tiny -> R.layout.layout_ad_native_tiny
        }
        val nativeAdView = LayoutInflater.from(parent.context).inflate(layoutId, parent, false) as NativeAdView
        bind(nativeAdView, payload.ad)
        return nativeAdView
    }

    private fun bind(nativeAdView: NativeAdView, nativeAd: NativeAd) {
        val headlineView = nativeAdView.findViewById<TextView>(R.id.adHeadline)
        val bodyView = nativeAdView.findViewById<TextView>(R.id.adBody)
        val actionView = nativeAdView.findViewById<TextView>(R.id.adAction)
        val iconView = nativeAdView.findViewById<ImageView>(R.id.adIcon)
        val mediaView = nativeAdView.findViewById<MediaView?>(R.id.adMedia)
        val imageClose = nativeAdView.findViewById<ImageView?>(R.id.imageClose)
        nativeAdView.headlineView = headlineView
        nativeAdView.bodyView = bodyView
        nativeAdView.callToActionView = actionView
        nativeAdView.iconView = iconView

        headlineView.text = nativeAd.headline
        bodyView.text = nativeAd.body.orEmpty()
        bodyView.visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE

        actionView.text = nativeAd.callToAction.orEmpty()
        actionView.visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE

        val icon = nativeAd.icon?.drawable
        if (icon == null) {
            iconView.visibility = View.GONE
        } else {
            iconView.visibility = View.VISIBLE
            iconView.setImageDrawable(icon)
        }
//        if (AdsConfigBridge.natConfig?.switchOpen == 1) {
//            if (BlockUtils.isShowNativeAdCloseButton(context)) {
//                imageClose.visibility = View.VISIBLE
//                imageClose.setOnClickListener {
//                    val isOpen =
//                        Random.nextInt(100) < AdRemoteBridge.natConfig!!.jumpPercent
//                    Log.e("TAG", "bindNativeAd: isOpen = $isOpen")
//                    if (isOpen) {
//                        //打开广告
//                        action.performClick()
//                    } else {
//                        parent.removeAllViews()
//                        parent.visibility = View.GONE
//                    }
//                }
//            } else {
//                imageClose.visibility = View.GONE
//            }
//        } else {
//            imageClose.visibility = View.GONE
//        }
        // Next Gen 原生广告需要通过 registerNativeAd 完成素材注册，不能继续沿用旧版 setNativeAd 流程。
        nativeAdView.registerNativeAd(nativeAd, mediaView)
    }
}

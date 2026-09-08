package com.ysdc.aidpdf.ads.provider.admob

import android.view.ViewGroup
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.ysdc.aidpdf.ads.model.AdDisplayHandle

class AdMobDisplayHandle(
    private val parent: ViewGroup,
    private val child: android.view.View,
    private val nativeAd: NativeAd? = null,
    private val adView: AdView? = null
) : AdDisplayHandle {
    private var destroyed = false

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        if (child.parent === parent) {
            parent.removeView(child)
        }
        adView?.destroy()
        nativeAd?.destroy()
    }
}

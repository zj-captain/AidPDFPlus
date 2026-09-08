package com.ysdc.aidpdf.ads.provider.tradplus

import android.view.ViewGroup
import com.tradplus.ads.open.banner.TPBanner
import com.ysdc.aidpdf.ads.model.AdDisplayHandle

class TradPlusDisplayHandle(
    private val parent: ViewGroup,
    private val child: android.view.View,
    private val tpBanner: TPBanner? = null,
    private val onDestroyAction: (() -> Unit)? = null
) : AdDisplayHandle {
    private var destroyed = false

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        if (child.parent === parent) {
            parent.removeView(child)
        }
        onDestroyAction?.invoke()
        tpBanner?.onDestroy()
    }
}

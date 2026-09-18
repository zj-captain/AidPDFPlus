package com.ysdc.aidpdf.ads.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ads.core.AdsLogger

class FullScreenLoadingOverlay(private val activity: AppCompatActivity) {
    private var overlayView: View? = null

    fun show() {
        if (activity.isFinishing || activity.isDestroyed) {
            AdsLogger.w("全屏广告 loading 显示被忽略：Activity 已结束")
            return
        }
        if (overlayView != null) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val label = TextView(activity).apply {
            text = activity.getString(R.string.ad_loading)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(32, 20, 32, 20)
            setBackgroundColor(0xAA000000.toInt())
            gravity = Gravity.CENTER
        }
        val container = FrameLayout(activity).apply {
            setBackgroundColor(0x33000000)
            isClickable = true
            isFocusable = true
            addView(
                label,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }
        content.addView(
            container,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        overlayView = container
    }

    fun dismiss() {
        val view = overlayView ?: return
        overlayView = null
        val parent = view.parent
        if (parent is ViewGroup) {
            parent.removeView(view)
        }
    }
}

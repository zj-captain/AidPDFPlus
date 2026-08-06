package com.ysdc.aidpdf.ad.store

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import android.os.Build
import android.util.DisplayMetrics
import com.google.android.gms.ads.LoadAdError
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdFormat
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.config.AdUnitConfig
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
import com.ysdc.aidpdf.tracking.AidEventHub.reportAdClick
import com.ysdc.aidpdf.tracking.AidEventHub.reportAdClose
import com.ysdc.aidpdf.tracking.AidEventHub.reportAdLoaded
import com.ysdc.aidpdf.tracking.AidEventHub.reportAdShow
import com.ysdc.aidpdf.tracking.AidEventHub.reportStartLoading
import java.util.UUID

class BannerAdStore(private val scene: AdScene) {

    private val candidates = mutableListOf<AdUnitConfig>()
    private var bannerView: AdView? = null
    private var currentParent: ViewGroup? = null
    private var loading = false
    val requestId: String = UUID.randomUUID().toString().replace("-", "")
    fun configure(units: List<AdUnitConfig>) {
        candidates.clear()
        candidates.addAll(units.filter { unit -> unit.isAdMob && unit.format == AdFormat.Banner && unit.unitId.isNotBlank() })
        destroy(currentParent)
    }

    fun show(activity: AppCompatActivity, parent: ViewGroup, onImpression: () -> Unit = {}) {
        if (loading || candidates.isEmpty()) return
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (currentParent === parent && bannerView?.parent === parent) {
            parent.visibility = View.VISIBLE
            return
        }

        destroy(parent)
        currentParent = parent
        parent.visibility = View.GONE
        loading = true

        val request = AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, collapsibleExtras())
            .build()
        var index = 0

        fun loadNext() {
            val unit = candidates.getOrNull(index)
            if (unit == null) {
                loading = false
                destroy(parent)
                return
            }
            index += 1
            if (BlockUtils.shouldBlockAds(activity)) {
                loading = false
                destroy(parent)
                return
            }
            val view = AdView(activity).apply {
                setAdSize(getAdaptiveAdSize(activity))
                adUnitId = unit.unitId
                adListener = object : AdListener() {
                    override fun onAdClicked() {
//                        AdEventTracker.reportClick(scene.trackingKey)
                        reportAdClick(
                            2,
                            "banner",
                            scene.trackingKey,
                            adUnitId,
                            0.0,
                            bannerView?.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "Admob",
                            requestId
                        )
                        ReminderTriggerCenter.onAdClicked()
                    }

                    override fun onAdLoaded() {
                        reportAdLoaded(
                            2,
                            "banner",
                            scene.trackingKey,
                            adUnitId,
                            0.0,
                            "Admob",
                            200,
                            "",
                            requestId
                        )
//                        AdEventTracker.reportLoadSucceeded(scene.trackingKey)
                        if (bannerView !== this@apply || !activity.lifecycle.currentState.isAtLeast(
                                Lifecycle.State.RESUMED
                            )
                        ) {
                            abandon(this@apply, parent)
                            return
                        }
                        loading = false
                        parent.removeAllViews()
                        val container = FrameLayout(activity).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                Gravity.CENTER
                            )
                        }
                        container.addView(
                            this@apply,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                Gravity.CENTER
                            )
                        )
                        parent.addView(container)
                        parent.visibility = View.VISIBLE
                        AidAdHub.log("${scene.remoteKey} banner loaded")
                    }

                    override fun onAdImpression() {
                        AdEventTracker.reportImpression(scene.trackingKey)
                        runCatching { onImpression() }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        reportAdLoaded(
                            2,
                            "banner",
                            scene.trackingKey,
                            adUnitId,
                            0.0,
                            "Admob",
                            error.code,
                            error.message,
                            requestId
                        )
//                        AdEventTracker.reportLoadFailed(
//                            scene.trackingKey,
//                            error.code,
//                            error.message
//                        )
                        AidAdHub.log("${scene.remoteKey} banner failed: ${error.message}")
                        this@apply.destroy()
                        if (bannerView === this@apply) {
                            bannerView = null
                        }
                        loadNext()
                    }

                    override fun onAdClosed() {
                        super.onAdClosed()
                        reportAdClose(
                            2,
                            "banner",
                            scene.trackingKey,
                            adUnitId,
                            0,
                            bannerView?.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "Admob",
                            requestId
                        )
                    }
                }
                setOnPaidEventListener { value ->
                    reportAdShow(
                        2, "banner", scene.trackingKey,
                        adUnitId, value.valueMicros / 1_000_000.0 * 1000,
                        this.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "Admob",
                        200, "", requestId
                    )
                    AdEventTracker.reportPaidValue(scene.trackingKey, unit, value, responseInfo)
                }
            }
            bannerView = view
            reportStartLoading(2, "banner", scene.trackingKey, unit.unitId, requestId)
//            AdEventTracker.reportLoadStarted(scene.trackingKey)
            view.loadAd(request)
            AidAdHub.log("${scene.remoteKey} banner loading")
        }

        loadNext()
    }

    fun destroy(parent: ViewGroup? = currentParent) {
        parent?.removeAllViews()
        parent?.visibility = View.GONE
        bannerView?.destroy()
        bannerView = null
        if (parent == currentParent) {
            currentParent = null
        }
        loading = false
    }

    private fun abandon(view: AdView, parent: ViewGroup) {
        view.destroy()
        if (bannerView === view) {
            bannerView = null
            loading = false
            parent.removeAllViews()
            parent.visibility = View.GONE
        }
    }

    private fun collapsibleExtras(): Bundle {
        return Bundle().apply {
            putString("collapsible", "bottom")
            putString("collapsible_request_id", UUID.randomUUID().toString())
        }
    }

    private fun getAdaptiveAdSize(activity: AppCompatActivity): AdSize {
        val adWidthPixels: Float
        val density: Float

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = activity.windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            adWidthPixels = bounds.width().toFloat()
            density = activity.resources.displayMetrics.density
        } else {
            val outMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(outMetrics)
            adWidthPixels = outMetrics.widthPixels.toFloat()
            density = outMetrics.density
        }

        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }
}

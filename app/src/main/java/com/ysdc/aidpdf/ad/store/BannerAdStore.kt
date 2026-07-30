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
import com.google.android.gms.ads.LoadAdError
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdFormat
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.config.AdUnitConfig
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
import java.util.UUID

class BannerAdStore(private val scene: AdScene) {

    private val candidates = mutableListOf<AdUnitConfig>()
    private var bannerView: AdView? = null
    private var currentParent: ViewGroup? = null
    private var loading = false

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
                setAdSize(AdSize.BANNER)
                adUnitId = unit.unitId
                adListener = object : AdListener() {
                    override fun onAdClicked() {
                        AdEventTracker.reportClick(scene.trackingKey)
                        ReminderTriggerCenter.onAdClicked()
                    }

                    override fun onAdLoaded() {
                        AdEventTracker.reportLoadSucceeded(scene.trackingKey)
                        if (bannerView !== this@apply || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            abandon(this@apply, parent)
                            return
                        }
                        loading = false
                        parent.removeAllViews()
                        parent.addView(this@apply, bannerLayoutParams(parent))
                        parent.visibility = View.VISIBLE
                        AidAdHub.log("${scene.remoteKey} banner loaded")
                    }

                    override fun onAdImpression() {
                        AdEventTracker.reportImpression(scene.trackingKey)
                        runCatching { onImpression() }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        AdEventTracker.reportLoadFailed(scene.trackingKey, error.code, error.message)
                        AidAdHub.log("${scene.remoteKey} banner failed: ${error.message}")
                        this@apply.destroy()
                        if (bannerView === this@apply) {
                            bannerView = null
                        }
                        loadNext()
                    }
                }
                setOnPaidEventListener { value ->
                    AdEventTracker.reportPaidValue(scene.trackingKey, unit, value, responseInfo)
                }
            }
            bannerView = view
            AdEventTracker.reportLoadStarted(scene.trackingKey)
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

    private fun bannerLayoutParams(parent: ViewGroup): ViewGroup.LayoutParams {
        if (parent is FrameLayout) {
            return FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        return ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

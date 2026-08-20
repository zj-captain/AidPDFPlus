package com.ysdc.aidpdf.ad.store

import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import com.ysdc.aidpdf.ad.AdsLimitManager
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdFormat
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.config.AdUnitConfig
import com.ysdc.aidpdf.ad.core.AdLease
import com.ysdc.aidpdf.ad.core.AdRenderRequest
import com.ysdc.aidpdf.ad.google.AdmobNativeAd
import com.ysdc.aidpdf.ad.google.NativeAdSize

class NativeAdStore(private val scene: AdScene) : QueuedAdStore<AdmobNativeAd>(scene) {

    override fun createAd(unit: AdUnitConfig): AdmobNativeAd? {
        if (!unit.isAdMob || unit.unitId.isBlank()) return null
        if (unit.format != AdFormat.Native) return null
        return AdmobNativeAd(sceneName = scene.trackingKey, config = unit)
    }

    override fun waitUntilReady(context: Context, callback: (Boolean) -> Unit) {
        super.waitUntilReady(context, callback)
    }

    fun canShow(activity: AppCompatActivity): Boolean {
        return hasReady() && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    fun show(
        activity: AppCompatActivity,
        parent: ViewGroup,
        size: NativeAdSize,
        sceneOverride: String = scene.trackingKey,
        onImpression: () -> Unit = {},
        onShown: (AdLease) -> Unit = {}
    ) {
        if (!canShow(activity)) return
        if (!AdsLimitManager.canShow()) {
            val state = AdsLimitManager.getState()
            AidAdHub.log("广告位=${scene.remoteKey} showNativeAd: 已达到每日广告展示上限，dayKey=${state.dayKey}, shownCount=${state.shownCount}, limit=${AdsLimitManager.config.ac_ads_limit}")
            return
        }
        val ad = take() ?: return
        ad.sceneName = sceneOverride
        if (!AdsLimitManager.recordShow()) {
            AidAdHub.log("广告位=${scene.remoteKey} showNativeAd: 展示前记录次数失败，已达到每日广告展示上限")
            return
        }
        parent.isVisible = true
        ad.present(
            AdRenderRequest(
                activity = activity,
                container = parent,
                nativeSize = size,
                onImpression = onImpression
            )
        )
        onShown(ad)
        load(activity)
    }
}

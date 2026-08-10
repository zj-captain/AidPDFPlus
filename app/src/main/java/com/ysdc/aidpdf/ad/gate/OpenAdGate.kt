package com.ysdc.aidpdf.ad.gate

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.store.appInstance

object OpenAdGate {

    fun prepare(context: Context = appInstance) {
        AidAdHub.loadFullScreen(context, AdScene.Launch)
    }

    fun ready(activity: AppCompatActivity): Boolean {
        return AidAdHub.fullScreenReady(activity, AdScene.Launch)
    }

    fun showThenContinue(
        activity: AppCompatActivity,
        trackingScene: String = AdScene.Launch.trackingKey,
        trackingType: String? = null,
        onShown: () -> Unit = {},
        next: () -> Unit
    ) {
        AdEventTracker.reportChance(trackingScene, trackingType ?: "start")
        AidAdHub.showFullScreen(
            activity = activity,
            scene = AdScene.Launch,
            loadingDelayMillis = 0L,
            trackingScene = trackingScene,
            trackingType = trackingType,
            onShown = onShown,
            onClosed = next
        )
    }
}

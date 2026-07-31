package com.ysdc.aidpdf.ad.store

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdFormat
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.config.AdUnitConfig
import com.ysdc.aidpdf.ad.core.AdRenderRequest
import com.ysdc.aidpdf.ad.google.AdmobFullScreenAd
import com.ysdc.aidpdf.ui.pdf.TaskLoadingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FullScreenAdStore(private val scene: AdScene) : QueuedAdStore<AdmobFullScreenAd>(scene) {

    override fun createAd(unit: AdUnitConfig): AdmobFullScreenAd? {
        if (!unit.isAdMob || unit.unitId.isBlank()) return null
        if (unit.format != AdFormat.Open && unit.format != AdFormat.Interstitial) return null
        return AdmobFullScreenAd(sceneName = scene.trackingKey, config = unit)
    }

    fun canShow(activity: AppCompatActivity): Boolean {
        val ad = peek() ?: return false
        val isOpenAd = ad.config.format == AdFormat.Open
        return activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                AidAdHub.fullScreenIntervalReady(isOpenAd)
    }

    fun show(
        activity: AppCompatActivity,
        loadingDelayMillis: Long = DEFAULT_LOADING_DELAY_MILLIS,
        sceneOverride: String = scene.trackingKey,
        trackingType: String? = null,
        onShown: () -> Unit = {},
        onClosed: () -> Unit
    ) {
        if (!canShow(activity)) {
            onClosed()
            load(activity)
            return
        }

        val ad = take()
        if (ad == null) {
            onClosed()
            load(activity)
            return
        }
        ad.sceneName = sceneOverride
        activity.lifecycleScope.launch(Dispatchers.Main) {
            val loadingDialog = if (loadingDelayMillis > 0L) showLoading(activity) else null
            if (loadingDelayMillis > 0L) {
                delay(loadingDelayMillis)
                loadingDialog?.dismissAllowingStateLoss()
            }
            ad.present(
                AdRenderRequest(
                    activity = activity,
                    trackingType = trackingType,
                    onPresented = onShown,
                    onFinished = {
                        ad.release()
                        onClosed()
                        load(activity)
                    }
                )
            )
        }
    }

    private fun showLoading(activity: AppCompatActivity): TaskLoadingDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null
        if (activity.supportFragmentManager.isStateSaved) return null
        return runCatching {
            TaskLoadingDialog.newInstance(R.string.ad_loading).also { dialog ->
                dialog.show(activity.supportFragmentManager, LOADING_DIALOG_TAG)
            }
        }.getOrNull()
    }

    private companion object {
        private const val DEFAULT_LOADING_DELAY_MILLIS = 500L
        private const val LOADING_DIALOG_TAG = "ad_full_screen_loading"
    }
}

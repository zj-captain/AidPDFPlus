package com.ysdc.aidpdf.ad.google

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.ysdc.aidpdf.ad.AdEventTracker
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdUnitConfig
import com.ysdc.aidpdf.ad.core.AdLoadResult
import com.ysdc.aidpdf.ad.core.AdRenderRequest
import com.ysdc.aidpdf.ad.core.CachedAd
import com.ysdc.aidpdf.databinding.LayoutAdNativeLargeBinding
import com.ysdc.aidpdf.databinding.LayoutAdNativeMediumBinding
import com.ysdc.aidpdf.databinding.LayoutAdNativeTinyBinding
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
import java.util.UUID

class AdmobNativeAd(
    override var sceneName: String,
    override val config: AdUnitConfig,
    override val requestId: String = UUID.randomUUID().toString().replace("-", ""),
    override var loadedAtMillis: Long = System.currentTimeMillis()
) : CachedAd {

    private val request = AdRequest.Builder().build()
    private var nativeAd: NativeAd? = null
    private var impressionSent = false
    private var impressionCallback: () -> Unit = {}

    override fun load(context: Context, callback: (AdLoadResult) -> Unit) {
        AidAdHub.log("$sceneName native loading id=$requestId")
        AdEventTracker.reportLoadStarted(sceneName)
        AdLoader.Builder(context, config.unitId)
            .forNativeAd { ad ->
                if (BlockUtils.shouldCheckTestAdDevice()) {
                    BlockUtils.updateTestAdDevice(
                        AdmobTestDeviceProbe.isTestAdDevice(context, ad.headline)
                    )
                }
                nativeAd?.destroy()
                nativeAd = ad
                loadedAtMillis = System.currentTimeMillis()
                AdEventTracker.reportLoadSucceeded(sceneName)
                callback(AdLoadResult.Loaded)
            }
            .withAdListener(object : AdListener() {
                override fun onAdClicked() {
                    AdEventTracker.reportClick(sceneName)
                    ReminderTriggerCenter.onAdClicked()
                }

                override fun onAdImpression() {
                    AdEventTracker.reportImpression(sceneName)
                    notifyImpression()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdEventTracker.reportLoadFailed(sceneName, error.code, error.message)
                    callback(AdLoadResult.Failed(error.message))
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()
            .loadAd(request)
    }

    override fun present(request: AdRenderRequest) {
        val ad = nativeAd ?: return
        val parent = request.container ?: return
        impressionSent = false
        impressionCallback = request.onImpression
        ad.setOnPaidEventListener { value ->
            AidAdHub.log("$sceneName paid value=${value.valueMicros} currency=${value.currencyCode}")
            AdEventTracker.reportPaidValue(sceneName, config, value, ad.responseInfo)
        }
        val adView = createView(
            activity = request.activity,
            parent = parent,
            ad = ad,
            size = request.nativeSize ?: NativeAdSize.Tiny
        )
        parent.post {
            parent.removeAllViews()
            parent.addView(adView)
            AidAdHub.log("$sceneName native shown id=$requestId")
        }
    }

    override fun release() {
        nativeAd?.destroy()
        nativeAd = null
        impressionCallback = {}
    }

    private fun createView(
        activity: AppCompatActivity,
        parent: ViewGroup,
        ad: NativeAd,
        size: NativeAdSize
    ): NativeAdView {
        return when (size) {
            NativeAdSize.Large -> {
                val binding = LayoutAdNativeLargeBinding.inflate(LayoutInflater.from(activity), parent, false)
                binding.root.bindNativeAd(ad, binding.adIcon, binding.adHeadline, binding.adBody, binding.adAction, binding.adMedia)
            }
            NativeAdSize.Medium -> {
                val binding = LayoutAdNativeMediumBinding.inflate(LayoutInflater.from(activity), parent, false)
                binding.root.bindNativeAd(ad, binding.adIcon, binding.adHeadline, binding.adBody, binding.adAction, binding.adMedia)
            }
            NativeAdSize.Tiny -> {
                val binding = LayoutAdNativeTinyBinding.inflate(LayoutInflater.from(activity), parent, false)
                binding.root.bindNativeAd(ad, binding.adIcon, binding.adHeadline, binding.adBody, binding.adAction, null)
            }
        }
    }

    private fun NativeAdView.bindNativeAd(
        ad: NativeAd,
        icon: ImageView,
        headline: TextView,
        body: TextView,
        action: TextView,
        media: MediaView?
    ): NativeAdView {
        iconView = icon.apply {
            val iconDrawable = ad.icon?.drawable
            isVisible = iconDrawable != null
            setImageDrawable(iconDrawable)
        }
        headlineView = headline.apply {
            text = ad.headline.orEmpty()
        }
        bodyView = body.apply {
            val copy = ad.body.orEmpty()
            isVisible = copy.isNotBlank()
            text = copy
        }
        callToActionView = action.apply {
            text = ad.callToAction?.takeIf { it.isNotBlank() } ?: text
        }
        media?.let { view ->
            mediaView = view.apply {
                mediaContent = ad.mediaContent
                setImageScaleType(ImageView.ScaleType.CENTER_CROP)
            }
        }
        setNativeAd(ad)
        return this
    }

    private fun notifyImpression() {
        if (impressionSent) return
        impressionSent = true
        runCatching { impressionCallback() }
    }
}

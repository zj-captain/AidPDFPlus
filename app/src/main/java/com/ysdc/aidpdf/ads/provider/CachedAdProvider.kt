package com.ysdc.aidpdf.ads.provider

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.ysdc.aidpdf.ads.config.AdsFormat
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsUnitConfig
import com.ysdc.aidpdf.ads.core.AdsExceptionInfo
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.NativeRenderRequest

interface CachedAdProvider {
    fun supports(platform: AdsPlatform, format: AdsFormat): Boolean

    fun load(
        config: AdsUnitConfig,
        appContext: Context,
        activity: Activity?,
        callback: (Result<Any>) -> Unit
    )

    fun showFullScreen(
        payload: Any,
        activity: AppCompatActivity,
        onShown: () -> Unit,
        onClosed: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit,
        trackingScene: String? = null,
        trackingType: String? = null,
        ecpm: Double? = null
    )

    fun showNative(
        payload: Any,
        activity: AppCompatActivity,
        parent: ViewGroup,
        request: NativeRenderRequest,
        onShown: () -> Unit,
        onImpression: () -> Unit,
        onFailed: (AdsExceptionInfo) -> Unit,
        trackingScene: String? = null,
        ecpm: Double? = null
    ): AdDisplayHandle?

    fun destroyPayload(payload: Any)
}

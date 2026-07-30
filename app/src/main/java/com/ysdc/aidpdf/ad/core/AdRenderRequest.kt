package com.ysdc.aidpdf.ad.core

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.ysdc.aidpdf.ad.google.NativeAdSize

data class AdRenderRequest(
    val activity: AppCompatActivity,
    val container: ViewGroup? = null,
    val nativeSize: NativeAdSize? = null,
    val trackingType: String? = null,
    val onImpression: () -> Unit = {},
    val onPresented: () -> Unit = {},
    val onFinished: () -> Unit = {}
)

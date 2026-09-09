package com.ysdc.aidpdf.ads.consent

import androidx.appcompat.app.AppCompatActivity

/**
 * UMP 同意管理预留占位。
 *
 * 后续需要接入 com.google.android.ump 完整流程（原 AidUmpGate 逻辑）：
 * 1. 首次启动记住国家码 firstConsentCountryCode
 * 2. 非 EEA 国家或已完成同意 -> 直接放行
 * 3. EEA 国家 -> requestConsentInfoUpdate -> loadAndShowConsentFormIfRequired
 * 4. 完成后回调业务，并写入 hasCompletedUmpConsent
 *
 * 当前为占位实现：直接放行，保证启动流程可编译、可运行。
 */
object AidUmpGate {

    fun canLoadAdsBeforeConsent(): Boolean = true

    fun requestBeforeAds(activity: AppCompatActivity, onFinished: () -> Unit) {
        // TODO: 接入完整 UMP 同意流程后再替换此占位实现。
        onFinished()
    }
}

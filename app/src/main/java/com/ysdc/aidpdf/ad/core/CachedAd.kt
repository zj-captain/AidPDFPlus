package com.ysdc.aidpdf.ad.core

import android.content.Context
import com.loft.vertsdk.VertSDK
import com.ysdc.aidpdf.ad.config.AdUnitConfig

interface CachedAd : AdLease {
    override var sceneName: String
    val config: AdUnitConfig
    var loadedAtMillis: Long

    fun load(context: Context, callback: (AdLoadResult) -> Unit)

    fun present(request: AdRenderRequest)

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis - loadedAtMillis >= config.ttlSeconds * 1000L
    }

    fun createId(): String {
        return VertSDK.getDeviceId() + System.currentTimeMillis()
    }
}

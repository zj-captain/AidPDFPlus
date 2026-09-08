package com.ysdc.aidpdf.ads.init

import android.app.Application
import com.tradplus.ads.base.GlobalTradPlus
import com.tradplus.ads.open.TradPlusSdk
import com.ysdc.aidpdf.ads.core.AdsLogger

object TradPlusInitializer {
    private const val TRAD_PLUS_APP_ID = "TODO_TRADPLUS_APP_ID"

    @Volatile
    private var initialized = false

    fun initialize(application: Application) {
        if (initialized) {
            AdsLogger.d("TradPlus 初始化已完成，忽略重复调用")
            return
        }
        runCatching {
            // 这里预留 TradPlus App ID，后续由业务自行替换为正式值。
            TradPlusSdk.initSdk(application, TRAD_PLUS_APP_ID)
            // 初始化后立即刷新 application context，降低热启动展示时 context 失效风险。
            GlobalTradPlus.getInstance().refreshContext(application)
            initialized = true
            AdsLogger.d("TradPlus 初始化完成")
        }.onFailure {
            AdsLogger.e("TradPlus 初始化失败：${it.message}", it)
        }
    }

    fun refreshActivity(activity: android.app.Activity) {
        runCatching {
            GlobalTradPlus.getInstance().refreshContext(activity)
            AdsLogger.d("TradPlus Activity context 已刷新")
        }.onFailure {
            AdsLogger.w("TradPlus Activity context 刷新失败：${it.message}", it)
        }
    }

    fun isInitialized(): Boolean = initialized
}

package com.ysdc.aidpdf.ads.init

import android.app.Application
import com.tradplus.ads.base.GlobalTradPlus
import com.tradplus.ads.open.TradPlusSdk
import com.ysdc.aidpdf.ads.core.AdsLogger

object TradPlusInitializer {
    private const val TRAD_PLUS_APP_ID = "3BA470A2B9FED0E184DD0A0C8329B811"

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
            val settingParam = HashMap<String, Any>()
            val unitIds = arrayOf(
                "CF08D70991970B5C90747A9B20220F12",
                "6EF26876E9E56CD96DD4F01E2A1D9412",
                "3E1F59FFE26B1D4BBC3A7C2E51D57A12",
                "7879155BC6A5285C67D3C003D40D7E12",
                "161079FEE749053E5963BF20648FF012",
                "AC5896E2EAF7F1AD848820F04588C512"
            )
            settingParam["autoload_close"] = unitIds
            TradPlusSdk.setSettingDataParam(settingParam)
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

package com.ysdc.aidpdf.ads.init

import android.app.Application
import com.tradplus.ads.base.GlobalTradPlus
import com.tradplus.ads.open.TradPlusSdk
import com.ysdc.aidpdf.ads.core.AdsLogger

object TradPlusInitializer {
    private const val TRAD_PLUS_APP_ID = "A77C9A8B618E88EF72DC1E05CFB88A11"

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
                "4A1E0CCC2D7D9C8F146EDD76F3BD6E12",
                "5041097CA9419D1C1D05A61F8AEA9212",
                "0EEBA179B9D67F343D3FB7A19D59C912",
                "386CFFD673E1FB10106E36649FBDE312"
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

package com.ysdc.aidpdf.ads.init

import android.app.Application
import com.ysdc.aidpdf.ads.core.AdsLogger

object AdsInitializer {
    fun initialize(application: Application) {
        GmaInitializer.initialize(application)
        TradPlusInitializer.initialize(application)
        AdsLogger.d("广告模块初始化入口执行完成")
    }
}

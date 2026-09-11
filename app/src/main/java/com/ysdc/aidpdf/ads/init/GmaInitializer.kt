package com.ysdc.aidpdf.ads.init

import android.app.Application
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.ysdc.aidpdf.ads.core.AdsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object GmaInitializer {
    @Volatile
    private var initialized = false

    @Volatile
    private var initializing = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize(application: Application) {
        if (initialized || initializing) {
            AdsLogger.d("GMA 初始化已完成或正在进行，忽略重复调用")
            return
        }
        initializing = true
        // 文档明确要求在后台线程初始化，避免主线程阻塞或 ANR。
        scope.launch {
            runCatching {
                MobileAds.initialize(
                    application,
                    InitializationConfig.Builder("ca-app-pub-3940256099942544~3347511713").build()
                ) {
                    initialized = true
                    initializing = false
                    AdsLogger.d("GMA 初始化完成")
                }
            }.onFailure {
                initializing = false
                AdsLogger.e("GMA 初始化失败：${it.message}", it)
            }
        }
    }

    fun isInitialized(): Boolean = initialized
}

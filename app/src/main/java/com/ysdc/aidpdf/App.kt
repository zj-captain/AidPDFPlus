package com.ysdc.aidpdf

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.ysdc.aidpdf.ads.Ads
import com.ysdc.aidpdf.ads.config.AdsConfigBridge
import com.ysdc.aidpdf.app.AppVisibilityTracker
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.core.block.InstallReferrerRepository
import com.ysdc.aidpdf.reminder.alive.ReminderFcmInitializer
import com.ysdc.aidpdf.reminder.alive.ReminderKeepAliveJobService
import com.ysdc.aidpdf.reminder.alive.ReminderKeepAliveReceiver
import com.ysdc.aidpdf.reminder.config.ReminderConfigRepository
import com.ysdc.aidpdf.reminder.config.ReminderOverlayConfigRepository
import com.ysdc.aidpdf.reminder.front.ReminderBarManager
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter.registerReceivers
import com.ysdc.aidpdf.remote.RemoteConfigUtils
import com.ysdc.aidpdf.store.appInstance
import com.ysdc.aidpdf.store.hasSavedLanguageTag
import com.ysdc.aidpdf.store.languageTag
import com.ysdc.aidpdf.tracking.AidEventHub
import com.ysdc.aidpdf.tracking.CoreEventTracker

class App : Application() {

    private val visibilityTracker by lazy { AppVisibilityTracker(this) }

    override fun onCreate() {
        super.onCreate()
        appInstance = this
        registerReceivers(this)
        AidEventHub.initialize(this)
        ReminderConfigRepository.resetToLocalDefaults()
        ReminderOverlayConfigRepository.resetToLocalDefaults()
        ReminderTriggerCenter.start(this)
        ReminderBarManager.startIfAllowed(this)
        ReminderKeepAliveJobService.schedule(this)
        ReminderFcmInitializer.subscribeIfNeeded()
        visibilityTracker.register()
        CoreEventTracker.initialize(this)
        if (hasSavedLanguageTag) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        }
        PDFBoxResourceLoader.init(this)
        //广告初始化
        Ads.initialize(this)
        Ads.configure(AdsConfigBridge.localCatalog())


        InstallReferrerRepository.refreshIfMissing(this) {
            CoreEventTracker.reportReferrerUsers()
            warmEligibleAdInventory()
        }
        RemoteConfigUtils.initRemoteConfig(this, ::warmEligibleAdInventory)

        registerReceiver()
    }

    fun skipNextHotStart() {
        visibilityTracker.skipNextHotStart()
    }

    fun clearHotStartSkip() {
        visibilityTracker.clearHotStartSkip()
    }

    private fun warmEligibleAdInventory() {
        if (BlockUtils.shouldBlockAds(this)) return
//        InterstitialAdGate.prepareStartupInventory(this)
//        NativeAdGate.prepare(this, AdScene.MainNative)
//        NativeAdGate.prepare(this, AdScene.ResultNative)
    }

    fun registerReceiver() {
        // 仅保留动态注册，和 manifest 静态注册配合使用，避免重复接收
        // 这里不再注册 USER_UNLOCKED，因为它在部分设备上不稳定，静态注册也未必会触发
        // 由 PhoneLockRe 内部统一处理 SCREEN_ON / SCREEN_OFF / USER_PRESENT
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        runCatching {
            registerReceiver(ReminderKeepAliveReceiver(), filter)
        }.onFailure {
            Log.e("MyApplication", "registerReceiver failed", it)
        }
    }
}

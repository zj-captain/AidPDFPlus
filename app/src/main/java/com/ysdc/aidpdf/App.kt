package com.ysdc.aidpdf

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.gate.InterstitialAdGate
import com.ysdc.aidpdf.ad.gate.NativeAdGate
import com.ysdc.aidpdf.app.AppVisibilityTracker
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.core.block.InstallReferrerRepository
import com.ysdc.aidpdf.remote.RemoteConfigUtils
import com.ysdc.aidpdf.reminder.config.ReminderConfigRepository
import com.ysdc.aidpdf.reminder.config.ReminderOverlayConfigRepository
import com.ysdc.aidpdf.reminder.alive.ReminderKeepAliveJobService
import com.ysdc.aidpdf.reminder.alive.ReminderFcmInitializer
import com.ysdc.aidpdf.reminder.front.ReminderBarManager
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter.registerReceivers
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
        InstallReferrerRepository.refreshIfMissing(this) {
            CoreEventTracker.reportReferrerUsers()
            warmEligibleAdInventory()
        }
        AidAdHub.initialize(this)
        RemoteConfigUtils.initRemoteConfig(this, ::warmEligibleAdInventory)
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
}

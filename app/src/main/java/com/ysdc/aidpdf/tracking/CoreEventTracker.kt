package com.ysdc.aidpdf.tracking

import android.content.Context
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.core.block.DeviceSignals
import com.ysdc.aidpdf.store.hasReportedAdbUser
import com.ysdc.aidpdf.store.hasReportedBuyUser
import com.ysdc.aidpdf.store.hasReportedEmulatorUser
import com.ysdc.aidpdf.store.hasReportedInstall
import com.ysdc.aidpdf.store.hasReportedNoSimUser
import com.ysdc.aidpdf.store.hasReportedReferrerUser
import com.ysdc.aidpdf.store.hasReportedReviewUser
import com.ysdc.aidpdf.store.hasReportedTestAdsUser
import com.ysdc.aidpdf.store.installReferrer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object CoreEventTracker {

    private const val HEARTBEAT_INTERVAL_MILLIS = 5 * 60_000L
    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, _ -> }
    )
    private var heartbeatJob: Job? = null

    @Synchronized
    fun initialize(context: Context) {
        reportInstall()
        reportDeviceUsers(context.applicationContext)
        reportReferrerUsers()
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            delay(1_000L)
            while (true) {
                AidEventHub.track(TrackingEventNames.SESSION_BACK)
                delay(HEARTBEAT_INTERVAL_MILLIS)
            }
        }
    }

    fun reportLoadingView() {
        AidEventHub.track(TrackingEventNames.LOADING_VIEW)
    }

    fun reportForegroundSession() {
        AidEventHub.track(TrackingEventNames.SESSION)
    }

    @Synchronized
    fun reportReferrerUsers() {
        if (BlockUtils.isReviewUser()) {
            reportReviewUser()
        }
        if (BlockUtils.isReferrerUser() && !hasReportedReferrerUser) {
            hasReportedReferrerUser = true
            AidEventHub.track(TrackingEventNames.USER_REFERRER)
        }
        if (BlockUtils.isBuyUser() && !hasReportedBuyUser) {
            hasReportedBuyUser = true
            AidEventHub.track(
                TrackingEventNames.USER_BUY,
                mapOf("installreferrer" to installReferrer)
            )
        }
    }

    @Synchronized
    fun reportReviewUser() {
        if (hasReportedReviewUser) return
        hasReportedReviewUser = true
        AidEventHub.track(TrackingEventNames.USER_REVIEW)
    }

    @Synchronized
    fun reportTestAdsUser() {
        if (hasReportedTestAdsUser) return
        hasReportedTestAdsUser = true
        AidEventHub.track(TrackingEventNames.USE_TEST_ADS)
    }

    private fun reportInstall() {
        if (hasReportedInstall) return
        hasReportedInstall = true
        AidEventHub.track(TrackingEventNames.INSTALL)
    }

    private fun reportDeviceUsers(context: Context) {
        if (DeviceSignals.isEmulator() && !hasReportedEmulatorUser) {
            hasReportedEmulatorUser = true
            AidEventHub.track(TrackingEventNames.USE_EMULATOR)
        }
        if (DeviceSignals.hasNoSim(context) && !hasReportedNoSimUser) {
            hasReportedNoSimUser = true
            AidEventHub.track(TrackingEventNames.USE_NO_SIM)
        }
        if (DeviceSignals.isAdbEnabled(context) && !hasReportedAdbUser) {
            hasReportedAdbUser = true
            AidEventHub.track(TrackingEventNames.USE_ADB)
        }
    }
}

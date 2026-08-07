package com.ysdc.aidpdf.core.block

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.Log
import com.ysdc.aidpdf.BuildConfig
import com.ysdc.aidpdf.store.installReferrer
import com.ysdc.aidpdf.store.isSamSungAndKorean
import com.ysdc.aidpdf.tracking.CoreEventTracker
import java.util.concurrent.CopyOnWriteArrayList

object BlockUtils {
    private val defaultAllowedReferrers = listOf(
        "fb4a",
        "instagram",
        "ig4a",
        "glid",
        "gclid",
        "not%20set",
        "youtubeads",
        "%7B%22",
        "adjust",
        "bytedance",
        "livead"
    )

    @Volatile
    private var globalBlockEnabled = true

    @Volatile
    private var referrerBlockEnabled = true

    @Volatile
    private var adbBlockEnabled = true

    @Volatile
    private var testAdDevice: Boolean? = null

    @Volatile
    private var reviewerIpMatched = false

    private val defaultBlockedReferrers = listOf("gclid=123456789")
    private val allowedReferrers = CopyOnWriteArrayList(defaultAllowedReferrers)
    private val blockedReferrers = CopyOnWriteArrayList(defaultBlockedReferrers)

    fun applyGlobalSwitch(enabled: Boolean) {
        globalBlockEnabled = enabled
    }

    fun applyReferrerConfig(active: Boolean, content: List<String>) {
        referrerBlockEnabled = active
        allowedReferrers.replaceWith(content)
        CoreEventTracker.reportReferrerUsers()
    }

    fun applyBlockedReferrers(content: List<String>) {
        blockedReferrers.replaceWith(content)
        if (isReviewUser()) CoreEventTracker.reportReviewUser()
    }

    fun restoreDefaultReferrerConfig() {
        referrerBlockEnabled = true
        allowedReferrers.replaceWith(defaultAllowedReferrers)
        CoreEventTracker.reportReferrerUsers()
    }

    fun restoreDefaultBlockedReferrers() {
        blockedReferrers.replaceWith(defaultBlockedReferrers)
        if (isReviewUser()) CoreEventTracker.reportReviewUser()
    }

    fun applyAdbSwitch(enabled: Boolean) {
        adbBlockEnabled = enabled
    }

    fun shouldCheckTestAdDevice(): Boolean = testAdDevice == null

    @Synchronized
    fun updateTestAdDevice(isTestDevice: Boolean) {
        if (testAdDevice != null) return
        testAdDevice = isTestDevice
        if (isTestDevice) CoreEventTracker.reportTestAdsUser()
    }

    fun updateReviewerIpMatch(matched: Boolean) {
        reviewerIpMatched = matched
        if (matched) CoreEventTracker.reportReviewUser()
    }

    fun isReferrerUser(): Boolean = installReferrer.isNotBlank()

    fun isReviewUser(): Boolean = reviewerIpMatched || isBlockedReferrer()

    fun isBuyUser(): Boolean {
        val referrer = installReferrer
        if (referrer.isBlank()) return false
        if (isBlockedReferrer()) return false
        return allowedReferrers.any { value ->
            value.isNotBlank() && referrer.contains(value, ignoreCase = true)
        }
    }

    fun shouldBlockAds(context: Context): Boolean {
        if (BuildConfig.DEBUG) return false
        if (globalBlockEnabled) return true
        if (isReviewUser()) return true
        if (testAdDevice == true) return true
        if (isBlockedReferrer()) return true
        if (shouldBlockForReferrer()) return true
        if (DeviceSignals.hasNoSim(context)) return true
        if (DeviceSignals.isEmulator()) return true
        if (isSamSungAndKoreanFun()) return true  //三星且韩国不显示广告
        return adbBlockEnabled && DeviceSignals.isAdbEnabled(context)
    }

    /**
     * 是否显示评分弹窗
     */
    fun isShowRateDialog(context: Context): Boolean{
        if (!globalBlockEnabled) return true
        if (isReviewUser()) return false
        if (isBlockedReferrer()) return false
        if (shouldBlockForReferrer()) return false
        if (isSamSungAndKoreanFun()) return false
        if (testAdDevice == true) return false
        if (DeviceSignals.hasNoSim(context)) return false
        if (DeviceSignals.isEmulator()) return false
        if (adbBlockEnabled && DeviceSignals.isAdbEnabled(context)) return false
        return true
    }

    fun isShowNativeAdCloseButton(context: Context): Boolean{
        if (!globalBlockEnabled) return true
        if (isReviewUser()) return false
        if (isBlockedReferrer()) return false
        if (shouldBlockForReferrer()) return false
        if (isSamSungAndKoreanFun()) return false
        if (testAdDevice == true) return false
        if (DeviceSignals.hasNoSim(context)) return false
        if (DeviceSignals.isEmulator()) return false
        if (adbBlockEnabled && DeviceSignals.isAdbEnabled(context)) return false
        return true
    }

    fun isSamSungAndKoreanFun(): Boolean {
        return if (isSamSungAndKorean.isEmpty() || isSamSungAndKorean.isBlank()) {
            if (isSamSung() && isKorean()) {
                isSamSungAndKorean = "true"
                true
            } else {
                isSamSungAndKorean = "false"
                false
            }
        } else {
            isSamSungAndKorean == "true"
        }
    }

    //三星
    fun isSamSung(): Boolean = Build.MANUFACTURER.equals("samsung", true)

    //韩国
    fun isKorean(): Boolean = localCountry().equals("KR", true)
    private fun localCountry(): String {
        return Resources.getSystem().configuration.locales.get(0).country
    }

    fun samsungCategory(): SamsungCategory {
        if (!DeviceSignals.isSamsung()) return SamsungCategory.NotSamsung
        return if (DeviceSignals.countryCode() == KOREA_COUNTRY_CODE) {
            SamsungCategory.Korea
        } else {
            SamsungCategory.OutsideKorea
        }
    }

    private fun isBlockedReferrer(): Boolean {
        val referrer = installReferrer
        if (referrer.isBlank()) return false
        return blockedReferrers.any { value ->
            value.isNotBlank() && referrer.contains(value, ignoreCase = true)
        }
    }

    private fun shouldBlockForReferrer(): Boolean {
        if (!referrerBlockEnabled) return false
        val referrer = installReferrer
        if (referrer.isBlank()) return true
        return allowedReferrers.none { value ->
            value.isNotBlank() && referrer.contains(value, ignoreCase = true)
        }
    }

    private fun CopyOnWriteArrayList<String>.replaceWith(values: List<String>) {
        clear()
        addAll(values.map(String::trim).filter(String::isNotBlank))
    }

    enum class SamsungCategory {
        NotSamsung,
        OutsideKorea,
        Korea
    }

    private const val KOREA_COUNTRY_CODE = "KR"
}

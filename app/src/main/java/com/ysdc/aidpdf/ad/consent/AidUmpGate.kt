package com.ysdc.aidpdf.ad.consent

import androidx.appcompat.app.AppCompatActivity
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.ysdc.aidpdf.BuildConfig
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.store.firstConsentCountryCode
import com.ysdc.aidpdf.store.hasCompletedUmpConsent
import java.util.Locale

object AidUmpGate {

    private val consentCountries = setOf(
        "AT",
        "BE",
        "BG",
        "CH",
        "CY",
        "CZ",
        "DE",
        "DK",
        "EE",
        "ES",
        "FI",
        "FR",
        "GB",
        "GR",
        "HR",
        "HU",
        "IE",
        "IS",
        "IT",
        "LI",
        "LT",
        "LU",
        "LV",
        "MT",
        "NL",
        "NO",
        "PL",
        "PT",
        "RO",
        "SE",
        "SI",
        "SK"
    )

    private var requesting = false
    private val pendingCompletions = mutableListOf<() -> Unit>()

    fun canLoadAdsBeforeConsent(): Boolean {
        rememberFirstCountry()
        return hasCompletedUmpConsent || firstConsentCountryCode !in consentCountries
    }

    fun requestBeforeAds(activity: AppCompatActivity, onFinished: () -> Unit) {
        rememberFirstCountry()
        if (hasCompletedUmpConsent) {
            onFinished()
            return
        }
        if (firstConsentCountryCode !in consentCountries) {
            finishRequest(onFinished)
            return
        }
        pendingCompletions += onFinished
        if (requesting) return
        requesting = true

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            buildParameters(activity),
            { showFormIfRequired(activity, consentInformation) },
            { error ->
                AidAdHub.log("UMP info update failed: ${error.message}")
                finishPendingRequests()
            }
        )
    }

    private fun rememberFirstCountry() {
        if (firstConsentCountryCode.isNotBlank()) return
        firstConsentCountryCode = Locale.getDefault().country.uppercase(Locale.US)
    }

    private fun buildParameters(activity: AppCompatActivity): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG) {
            val debugSettings = ConsentDebugSettings.Builder(activity)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .build()
            builder.setConsentDebugSettings(debugSettings)
        }
        return builder.build()
    }

    private fun showFormIfRequired(
        activity: AppCompatActivity,
        consentInformation: ConsentInformation
    ) {
        if (!consentInformation.isConsentFormAvailable) {
            finishPendingRequests()
            return
        }
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
            if (error != null) {
                AidAdHub.log("UMP form failed: ${error.message}")
            }
            finishPendingRequests()
        }
    }

    private fun finishRequest(onFinished: () -> Unit) {
        hasCompletedUmpConsent = true
        requesting = false
        onFinished()
    }

    private fun finishPendingRequests() {
        hasCompletedUmpConsent = true
        requesting = false
        val callbacks = pendingCompletions.toList()
        pendingCompletions.clear()
        callbacks.forEach { callback -> callback() }
    }
}

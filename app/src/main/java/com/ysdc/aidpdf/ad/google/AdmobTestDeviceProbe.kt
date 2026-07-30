package com.ysdc.aidpdf.ad.google

import android.annotation.SuppressLint
import android.content.Context
import java.util.Locale

object AdmobTestDeviceProbe {

    fun isTestAdDevice(context: Context, headline: CharSequence?): Boolean {
        return matchesTestAdHeadline(
            headline = headline,
            localizedPrefix = localizedTestAdPrefix(context)
        )
    }

    internal fun matchesTestAdHeadline(
        headline: CharSequence?,
        localizedPrefix: String?
    ): Boolean {
        val normalizedHeadline = headline
            ?.toString()
            ?.trim()
            ?.lowercase(Locale.US)
            .orEmpty()
        if (normalizedHeadline.isBlank()) return false

        return sequenceOf(DEFAULT_TEST_AD_PREFIX, localizedPrefix)
            .filterNotNull()
            .map { prefix -> prefix.trim().lowercase(Locale.US) }
            .filter(String::isNotBlank)
            .any(normalizedHeadline::startsWith)
    }

    @SuppressLint("DiscouragedApi")
    private fun localizedTestAdPrefix(context: Context): String? {
        return runCatching {
            val resourceId = context.resources.getIdentifier(
                TEST_AD_RESOURCE_NAME,
                "string",
                context.packageName
            )
            if (resourceId == 0) null else context.getString(resourceId)
        }.getOrNull()
    }

    private const val DEFAULT_TEST_AD_PREFIX = "test ad"
    private const val TEST_AD_RESOURCE_NAME = "s7"
}

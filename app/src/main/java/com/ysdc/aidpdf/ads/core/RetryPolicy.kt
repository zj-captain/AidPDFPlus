package com.ysdc.aidpdf.ads.core

object RetryPolicy {
    const val MAX_RETRY_STAGE = 2

    fun delayMillis(retryStage: Int): Long {
        return when (retryStage) {
            0 -> 10_000L
            1 -> 20_000L
            else -> 40_000L
        }
    }
}

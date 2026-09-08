package com.ysdc.aidpdf.ads.core

object RetryPolicy {
    fun delayMillis(retryStage: Int): Long {
        return when (retryStage) {
            0 -> 1_000L
            1 -> 2_000L
            else -> 4_000L
        }
    }
}

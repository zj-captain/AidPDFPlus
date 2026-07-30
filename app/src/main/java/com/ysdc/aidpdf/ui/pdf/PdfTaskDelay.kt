package com.ysdc.aidpdf.ui.pdf

import android.os.SystemClock
import kotlinx.coroutines.delay

object PdfTaskDelay {

    private const val MIN_LOADING_DURATION_MS = 2_000L

    fun startedAt(): Long = SystemClock.elapsedRealtime()

    suspend fun waitUntilSatisfied(startedAt: Long) {
        val left = MIN_LOADING_DURATION_MS - (SystemClock.elapsedRealtime() - startedAt)
        if (left > 0L) delay(left)
    }
}

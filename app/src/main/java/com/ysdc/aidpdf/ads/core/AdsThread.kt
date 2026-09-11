package com.ysdc.aidpdf.ads.core

import android.os.Handler
import android.os.Looper

object AdsThread {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    fun runOnMain(block: () -> Unit) {
        if (isMainThread()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}

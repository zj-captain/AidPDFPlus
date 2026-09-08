package com.ysdc.aidpdf.ads.core

import android.util.Log
import com.ysdc.aidpdf.BuildConfig

object AdsLogger {
    private const val TAG = "AidAds"

    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(TAG, message)
        } else {
            Log.w(TAG, message, throwable)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, throwable)
        }
    }
}

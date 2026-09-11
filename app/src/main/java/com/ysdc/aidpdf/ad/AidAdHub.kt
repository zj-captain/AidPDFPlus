package com.ysdc.aidpdf.ad

import android.util.Log
import com.ysdc.aidpdf.BuildConfig

object AidAdHub {

    private const val TAG = "AidAdHub"


    fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}

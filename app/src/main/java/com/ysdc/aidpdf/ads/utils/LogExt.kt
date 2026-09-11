package com.ysdc.aidpdf.ads.utils

import android.util.Log

const val LOG_MAX_LENGTH = 3000

/** Debug日志 字符串扩展 */
fun String.logD(tag: String) {
    if (this.length > LOG_MAX_LENGTH) {
        // 超长日志分段打印，避免logcat截断
        var start = 0
        while (start < this.length) {
            val end = kotlin.math.min(start + LOG_MAX_LENGTH, this.length)
            val sub = this.substring(start, end)
            Log.d(tag, sub)
            start = end
        }
    } else {
        Log.d(tag, this)
    }
}

fun String.logI(tag: String) = Log.i(tag, this)
fun String.logW(tag: String) = Log.w(tag, this)
fun String.logE(tag: String, throwable: Throwable? = null) = Log.e(tag, this, throwable)

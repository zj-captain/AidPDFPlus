package com.ysdc.aidpdf.ad.core

interface AdLease {
    val sceneName: String
    val requestId: String

    fun release()
}


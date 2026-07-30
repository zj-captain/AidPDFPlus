package com.ysdc.aidpdf.ad.core

sealed class AdLoadResult {
    data object Loaded : AdLoadResult()
    data class Failed(val reason: String? = null) : AdLoadResult()
}


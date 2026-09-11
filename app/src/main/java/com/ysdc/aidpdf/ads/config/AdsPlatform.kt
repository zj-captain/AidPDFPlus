package com.ysdc.aidpdf.ads.config

enum class AdsPlatform {
    AdMob,
    TradPlus;

    companion object {
        fun fromRemote(value: String): AdsPlatform? {
            return when (value.trim().lowercase()) {
                "admob" -> AdMob
                "tp" -> TradPlus
                else -> null
            }
        }
    }
}

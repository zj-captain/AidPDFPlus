package com.ysdc.aidpdf.ads.config

enum class AdsFormat {
    Open,
    Interstitial,
    Native,
    Banner;

    companion object {
        fun fromRemote(value: String): AdsFormat? {
            return when (value.trim().lowercase()) {
                "op" -> Open
                "int" -> Interstitial
                "nat" -> Native
                "banner" -> Banner
                else -> null
            }
        }
    }
}

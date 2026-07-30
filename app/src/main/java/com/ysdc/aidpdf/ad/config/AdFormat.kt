package com.ysdc.aidpdf.ad.config

enum class AdFormat {
    Open,
    Interstitial,
    Native,
    Banner;

    companion object {
        fun fromRemote(value: String): AdFormat? {
            return when (value.trim().lowercase()) {
                "op", "open", "app_open" -> Open
                "int", "intt", "interstitial" -> Interstitial
                "nat", "native" -> Native
                "ban", "banner" -> Banner
                else -> null
            }
        }
    }
}


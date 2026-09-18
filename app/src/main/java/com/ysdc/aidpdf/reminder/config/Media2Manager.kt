package com.ysdc.aidpdf.reminder.config

import androidx.annotation.Keep
import com.google.gson.Gson
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.store.adsLimitStateJson
import java.util.Calendar

const val DEFAULT_MEDIA_CONFIG_JSON = """
{"switch":1,"intervalTime":30}
"""

@Keep
data class MediaConfig(
    var switch: Int = 1,
    var intervalTime: Int = 10
)

object Media2Manager {

    private val gson = Gson()

    @Volatile
    var config: MediaConfig = MediaConfig()
        private set

    @Synchronized
    fun applyConfig(json: String) {
        config = runCatching {
            gson.fromJson(
                json.ifBlank { DEFAULT_MEDIA_CONFIG_JSON },
                MediaConfig::class.java
            )
        }.getOrElse {
            MediaConfig()
        }
    }

}

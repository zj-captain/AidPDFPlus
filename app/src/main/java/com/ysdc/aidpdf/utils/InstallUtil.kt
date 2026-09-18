package com.ysdc.aidpdf.utils

import android.content.Context
import android.os.Build
import androidx.annotation.Keep
import com.google.gson.Gson
import com.ysdc.aidpdf.store.appInstance

const val DEFAULT_PlAY_CONFIG_JSON = """
{
  "google_play_block": 0
}
"""

@Keep
data class GooglePlayConfig(
    val google_play_block: Int = 0,
)
object InstallUtil {
    /**
     * 是否来自googlePlay的安装
     */
    fun isFromGooglePlay(context: Context = appInstance): Boolean {
        return try {
            val packageName = context.packageName
            val installerPackage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(packageName)
            }
            installerPackage == "com.android.vending"
        } catch (e: Exception) {
            false
        }
    }
    private val gson = Gson()

    @Volatile
    var config: GooglePlayConfig = GooglePlayConfig()
        private set
    @Synchronized
    fun applyConfig(json: String) {
        config = runCatching {
            gson.fromJson(json.ifBlank { DEFAULT_PlAY_CONFIG_JSON }, GooglePlayConfig::class.java)
        }.getOrElse {
            GooglePlayConfig()
        }
    }
}
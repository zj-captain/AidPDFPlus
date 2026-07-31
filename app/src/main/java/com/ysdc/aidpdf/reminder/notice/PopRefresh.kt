package com.ysdc.aidpdf.reminder.notice

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * 通知刷新配置
 */
@Keep
data class PopRefresh(
    @SerializedName("pop_refresh_switch")
    val popRefreshSwitch: Int,//通知刷新功能开关，1打开，0 关，默认0
    @SerializedName("times")
    val times: Int,//每次通知的发送次数，30次
    @SerializedName("interval")
    val interval: Int,//通知间隔时间，单位秒
)
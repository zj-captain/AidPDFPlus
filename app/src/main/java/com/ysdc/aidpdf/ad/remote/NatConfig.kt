package com.ysdc.aidpdf.ad.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class NatConfig(
    @SerializedName("switch_open")
    val switchOpen: Int,    //1表示开，0表示关; 开关控制原生广告是否展示关闭按钮
    @SerializedName("jump_percent")
    val jumpPercent: Int,   //误触百分比，概率%; 用户点击关闭按钮按概率跳转并关闭原生广告
)

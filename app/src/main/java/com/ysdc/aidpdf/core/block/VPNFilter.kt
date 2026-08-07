package com.ysdc.aidpdf.core.block

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission
import com.ysdc.aidpdf.tracking.AidEventHub
import com.ysdc.aidpdf.tracking.EventDelivery
import com.ysdc.aidpdf.tracking.TrackingEventNames.USER_VPN

object VPNFilter {
    var vpn_config_switch = 0  //vpn屏蔽逻辑开关，1打开，0关，默认1
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isVPN(context: Context): Boolean {
        return runCatching {
            // 统一使用Application上下文，避免Activity引用泄漏
            val appContext = context.applicationContext
            val cm =
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return@runCatching false
            cm.allNetworks.any { network ->
                val caps = cm.getNetworkCapabilities(network)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }.getOrDefault(false).apply {
            if (this){
                AidEventHub.track(USER_VPN, delivery = EventDelivery.Batched)
            }
        } // 任何异常均兜底返回false，杜绝崩溃
    }
}
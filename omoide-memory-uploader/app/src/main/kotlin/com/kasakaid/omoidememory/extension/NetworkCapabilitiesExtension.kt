package com.kasakaid.omoidememory.extension

import android.content.Context
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

object NetworkCapabilitiesExtension {
    fun NetworkCapabilities.ssid(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+
            val wifiInfo = transportInfo as? WifiInfo
            wifiInfo?.ssid?.removePrefix("\"")?.removeSuffix("\"")
        } else {
            // API 26–28 fallback
            @Suppress("DEPRECATION")
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
                ?.ssid
                ?.removePrefix("\"")
                ?.removeSuffix("\"")
        }
}

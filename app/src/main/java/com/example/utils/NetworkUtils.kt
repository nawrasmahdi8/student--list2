package com.example.utils

import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr.hostAddress.contains(".")) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            // Ignore
        }
        return null
    }
}

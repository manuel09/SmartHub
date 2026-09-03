package com.smarthub.player.data.api

import com.smarthub.player.VixApplication

object ProxyConfig {
    val STREAMVIX_URL: String
        get() = "http://127.0.0.1:${VixApplication.proxyManager.actualPrimaryPort}/"

    val EASY_PROXY_URL: String
        get() = "http://127.0.0.1:${VixApplication.proxyManager.actualPrimaryPort}"

    fun getProxiedUrl(originalUrl: String): String {
        return "$EASY_PROXY_URL/proxy/manifest.m3u8?url=${android.net.Uri.encode(originalUrl)}"
    }
}

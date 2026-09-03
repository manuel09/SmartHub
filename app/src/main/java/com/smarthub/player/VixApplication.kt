package com.smarthub.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.smarthub.player.data.proxy.LocalProxyManager

class VixApplication : Application(), ImageLoaderFactory {
    companion object {
        private var _proxyManager: LocalProxyManager? = null
        val proxyManager: LocalProxyManager
            get() {
                if (_proxyManager == null) {
                    _proxyManager = LocalProxyManager()
                }
                return _proxyManager!!
            }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(300)
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(0.15).build()
            }
            .diskCache {
                DiskCache.Builder().directory(cacheDir.resolve("coil_cache")).build()
            }
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        proxyManager.setContext(this)
    }
}

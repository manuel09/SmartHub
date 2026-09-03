package com.smarthub.player.data.proxy

import android.util.Log
import com.smarthub.player.VixApplication
import com.smarthub.player.BuildConfig

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import com.smarthub.player.R

class ProxyService : Service() {
    private val proxyManager = VixApplication.proxyManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d("ProxyService", "Service onCreate: Starting proxy setup.")
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "proxy_channel")
            .setContentTitle("Media Proxy Service")
            .setContentText("Streaming engine is running in background")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
        Log.d("ProxyService", "Foreground service started.")

        serviceScope.launch {
            try {
                LocalProxyManager.tmdbApiKey = BuildConfig.TMDB_API_KEY
                Log.d("ProxyService", "Attempting to start EasyProxy on port 7860.")
                val port7860 = proxyManager.start(7860)
                Log.d("ProxyService", "EasyProxy started on port $port7860.")
                
                Log.d("ProxyService", "Attempting to start Streamvix Addon Simulator on port 7000.")
                val port7000 = proxyManager.start(7000)
                Log.d("ProxyService", "Streamvix Addon Simulator started on port $port7000.")
            } catch (e: Exception) {
                Log.e("ProxyService", "Error starting proxy: ${e.message}", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ProxyService", "Service onStartCommand called.")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("ProxyService", "Service onDestroy: Stopping proxy.")
        serviceScope.cancel() // Cancella tutte le coroutine associate a questo scope
        proxyManager.stop()
        super.onDestroy()
        Log.d("ProxyService", "Proxy stopped and service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "proxy_channel",
                "Proxy Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

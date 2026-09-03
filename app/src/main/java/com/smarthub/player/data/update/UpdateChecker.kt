package com.smarthub.player.data.update

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object UpdateChecker {
    private const val TAG = "UpdateChecker"

    // URL del file JSON con i metadati dell'ultima versione.
    // Formato atteso:
    // {
    //   "versionCode": 2,
    //   "versionName": "1.1",
    //   "apkUrl": "https://.../app-release.apk",
    //   "changelog": "Testo note di rilascio",
    //   "mandatory": false
    // }
    private const val UPDATE_URL = "https://raw.githubusercontent.com/manuel09/SmartHub/main/update.json"

    private val gson = Gson()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun check(currentVersionCode: Long): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(UPDATE_URL)
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Update check failed: HTTP ${resp.code}")
                    return@withContext null
                }
                val body = resp.body?.string() ?: return@withContext null
                val info = gson.fromJson(body, UpdateInfo::class.java)
                val url = selectApkUrl(info)
                if (url == null) return@withContext null
                if (info.versionCode <= currentVersionCode) return@withContext null
                Log.d(TAG, "Update available: ${info.versionName} (${info.versionCode}) -> $url")
                info
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check error: ${e.message}")
            null
        }
    }

    private fun selectApkUrl(info: UpdateInfo): String? {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        val isV7a = abi.contains("armeabi-v7a", ignoreCase = true) ||
                (abi.contains("arm", ignoreCase = true) && !abi.contains("arm64", ignoreCase = true))
        return when {
            isV7a && info.apkUrlV7a.isNotBlank() -> info.apkUrlV7a
            info.apkUrl.isNotBlank() -> info.apkUrl
            info.apkUrlV7a.isNotBlank() -> info.apkUrlV7a
            else -> null
        }
    }
}

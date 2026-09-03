package com.smarthub.player.data.proxy

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class TelegramLoginInfo(
    val nonce: String,
    val deeplink: String,
    val weblink: String
)

class AltadefinizioneAuthManager {
    companion object {
        private const val TAG = "AltadefAuthManager"
        private const val BASE = "https://altadefinizionestreaming.tv"
        private const val HOST = "altadefinizionestreaming.tv"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    private val gson = Gson()
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val host = url.host
                    val existing = cookieStore[host] ?: mutableListOf()
                    for (c in cookies) {
                        existing.removeAll { it.name == c.name }
                        existing.add(c)
                    }
                    cookieStore[host] = existing
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookieStore[url.host] ?: emptyList()
                }
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun startLogin(next: String = "/"): TelegramLoginInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE/auth/bot/start?next=" + URLEncoder.encode(next, "UTF-8")
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "$BASE/")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "startLogin failed: ${resp.code}")
                    return@withContext null
                }
                val body = resp.body?.string() ?: return@withContext null
                val root = gson.fromJson(body, Map::class.java)
                val nonce = root["nonce"] as? String ?: return@withContext null
                val deeplink = root["deeplink"] as? String ?: ""
                val weblink = root["weblink"] as? String ?: ""
                Log.d(TAG, "startLogin ok, nonce=${nonce.take(12)}...")
                TelegramLoginInfo(nonce, deeplink, weblink)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startLogin error: ${e.message}")
            null
        }
    }

    suspend fun pollStatus(nonce: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE/auth/bot/status?t=" + URLEncoder.encode(nonce, "UTF-8")
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val root = gson.fromJson(body, Map::class.java)
                root["status"] as? String
            }
        } catch (e: Exception) {
            Log.e(TAG, "pollStatus error: ${e.message}")
            null
        }
    }

    suspend fun completeSession(nonce: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE/auth/bot/session?t=" + URLEncoder.encode(nonce, "UTF-8") + "&next=%2F"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "$BASE/")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "completeSession failed: ${resp.code}")
                    return@withContext null
                }
                resp.body?.string()

                val cookieHeader = cookieStore[HOST]
                    ?.joinToString("; ") { "${it.name}=${it.value}" }
                    ?.takeIf { it.isNotBlank() }

                if (cookieHeader.isNullOrBlank()) {
                    Log.e(TAG, "completeSession: no cookies captured")
                    return@withContext null
                }
                Log.d(TAG, "completeSession ok, cookie captured")
                cookieHeader
            }
        } catch (e: Exception) {
            Log.e(TAG, "completeSession error: ${e.message}")
            null
        }
    }
}

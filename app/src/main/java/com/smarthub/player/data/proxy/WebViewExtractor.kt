package com.smarthub.player.data.proxy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Extractor basato su WebView invisibile.
 * Bypassa Cloudflare caricando la pagina embed di vixsrc in un browser reale,
 * e intercetta la richiesta al file .m3u8 tramite shouldInterceptRequest.
 */
class WebViewExtractor(private val context: Context) {

    companion object {
        private const val TAG = "WebViewExtractor"
        private const val TIMEOUT_MS = 30_000L
        private val M3U8_PATTERNS = listOf(".m3u8", "/playlist", "/index.m3u8", "manifest.m3u8")
        private val SKIP_PATTERNS = listOf("google", "doubleclick", "analytics", "facebook", "sentry")
    }

    /**
     * Estrae l'URL dello stream dalla pagina embed di vixsrc.
     * @param embedUrl URL della pagina embed (es: https://vixsrc.to/embed/movie?tmdb=12345)
     * @return URL del file .m3u8 o null se non trovato entro il timeout
     */
    suspend fun extractStreamUrl(embedUrl: String): String? {
        Log.d(TAG, "Starting WebView extraction for: $embedUrl")

        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                Handler(Looper.getMainLooper()).post {
                    var webView: WebView? = null
                    var resumed = false

                    fun resume(url: String?) {
                        if (!resumed) {
                            resumed = true
                            Log.d(TAG, "Resuming with URL: $url")
                            Handler(Looper.getMainLooper()).post {
                                webView?.stopLoading()
                                webView?.destroy()
                                webView = null
                            }
                            cont.resume(url)
                        }
                    }

                    try {
                        webView = WebView(context).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                        "Chrome/122.0.0.0 Safari/537.36"
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): WebResourceResponse? {
                                    val url = request.url.toString()

                                    // Salta URL di tracker/analytics
                                    if (SKIP_PATTERNS.any { url.contains(it, ignoreCase = true) }) {
                                        return null
                                    }

                                    // Intercetta richieste al file .m3u8
                                    if (M3U8_PATTERNS.any { url.contains(it, ignoreCase = true) }) {
                                        Log.d(TAG, "🎯 Caught m3u8 URL: $url")
                                        resume(url)
                                    }
                                    return null
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?
                                ) {
                                    Log.e(TAG, "WebView error $errorCode for $failingUrl: $description")
                                }
                            }

                            loadUrl(embedUrl)
                        }

                        cont.invokeOnCancellation {
                            Handler(Looper.getMainLooper()).post {
                                webView?.stopLoading()
                                webView?.destroy()
                                webView = null
                            }
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "WebView setup error: ${e.message}")
                        resume(null)
                    }
                }
            }
        }.also {
            if (it == null) Log.e(TAG, "Extraction timed out after ${TIMEOUT_MS}ms for $embedUrl")
        }
    }
}

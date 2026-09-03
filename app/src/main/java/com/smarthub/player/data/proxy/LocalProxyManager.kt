package com.smarthub.player.data.proxy

import android.app.Application
import android.util.Log
import com.google.gson.Gson
import com.smarthub.player.data.local.AppSettings
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class LocalProxyManager {
    private val threadPool = Executors.newCachedThreadPool()
    private val serverSockets = mutableMapOf<Int, ServerSocket>()
    @Volatile var actualPrimaryPort: Int = -1
        private set
    private val gson = Gson()

    @Volatile
    var altadefinizioneExpired: Boolean = false
        private set

    private val client: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                private val store = mutableMapOf<String, MutableList<Cookie>>()
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    store[url.host] = cookies.toMutableList()
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return store[url.host] ?: emptyList()
                }
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    companion object {
        private const val TAG = "LocalProxyManager"
        private const val FIXED_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        private const val FIREFOX_UA = "Mozilla/5.0 (X11; Linux x86_64; rv:150.0) Gecko/20100101 Firefox/150.0"
        private const val SITE_URL = "https://vixsrc.to"
        private const val ALTADEF_BASE = "https://altadefinizionestreaming.tv"
        private const val ALTADEF_COOKIE = "sid=32234dfabd14e587764e84405e75e99856c6bef31c6b1752e19897b8ae3d4a21a"
        @Volatile var tmdbApiKey: String = ""
    }

    private var appContext: Application? = null

    fun setContext(ctx: Application) {
        appContext = ctx
    }

    private fun getAltadefCookie(): String {
        val saved = appContext?.let { AppSettings.getAltadefinizioneCookie(it) }
        return if (!saved.isNullOrBlank()) saved else ALTADEF_COOKIE
    }

    // Il CDN di Altadefinizione (hdmario) serve MP4 progressivi; solo gli stream HLS
    // (m3u8) vanno instradati sul proxy, gli altri vanno riprodotti direttamente.
    private fun isHlsUrl(url: String): Boolean {
        return Regex("""\.m3u8($|\?)""", RegexOption.IGNORE_CASE).containsMatchIn(url)
    }

    private fun looksLikeVideoUrl(url: String): Boolean {
        return isHlsUrl(url) ||
                url.contains(".mp4", ignoreCase = true) ||
                url.contains(".ts", ignoreCase = true) ||
                url.contains("/video", ignoreCase = true) ||
                url.contains("mime=video", ignoreCase = true)
    }

    private fun buildAltadefPlayUrl(url: String, cookie: String): String {
        val referer = URLEncoder.encode("$ALTADEF_BASE/", "UTF-8")
        val origin = URLEncoder.encode(ALTADEF_BASE, "UTF-8")
        val ua = URLEncoder.encode("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36", "UTF-8")
        val encodedCookie = URLEncoder.encode(cookie, "UTF-8")

        // HLS: instradato su manifest.m3u8 (riscrive i segmenti via proxy).
        // MP4 progressivo: instradato su /proxy/video (streaming con Range + header).
        val route = if (isHlsUrl(url)) "proxy/manifest.m3u8" else "proxy/video"
        return "http://127.0.0.1:$actualPrimaryPort/$route?url=" +
                URLEncoder.encode(url, "UTF-8") +
                "&h_Referer=" + referer +
                "&h_Origin=" + origin +
                "&h_UA=" + ua +
                "&h_Cookie=" + encodedCookie
    }

    fun start(requestedPort: Int): Int {
        Log.d(TAG, "Attempting to start server on port: $requestedPort")
        if (serverSockets.containsKey(requestedPort)) {
            Log.d(TAG, "Server already running on port: $requestedPort")
            return requestedPort
        }

        val portsToTry = mutableListOf(requestedPort)
        if (requestedPort != 0) {
            portsToTry.addAll((requestedPort + 1)..(requestedPort + 10))
        }

        for (tryPort in portsToTry) {
            val success = bindPort(tryPort, "127.0.0.1") || bindPort(tryPort, "::1")
            if (success) {
                if (actualPrimaryPort < 0) actualPrimaryPort = tryPort
                Log.d(TAG, "Server started successfully on port: $tryPort")
                return tryPort
            }
        }

        Log.e(TAG, "Failed to bind any port in range ${portsToTry.first()}..${portsToTry.last()}")
        return -1
    }

    private fun bindPort(port: Int, bindAddr: String): Boolean {
        if (serverSockets.containsKey(port)) return true
        try {
            val serverSocket = ServerSocket(port, 50, java.net.InetAddress.getByName(bindAddr))
            serverSocket.reuseAddress = true
            serverSockets[port] = serverSocket
            Log.d(TAG, "Server socket bound to $bindAddr:$port")

            threadPool.execute {
                Log.d(TAG, "Accepting connections on port $port ($bindAddr)...")
                try {
                    while (!serverSocket.isClosed) {
                        val clientSocket = serverSocket.accept()
                        threadPool.execute { handleRequest(clientSocket, port) }
                    }
                } catch (e: Exception) {
                    if (!serverSocket.isClosed) {
                        Log.e(TAG, "Accept loop error on port $port: ${e.message}", e)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind $bindAddr:$port: ${e.message}")
            return false
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping all servers.")
        serverSockets.values.forEach {
            try {
                it.close()
                Log.d(TAG, "Server on port ${it.localPort} stopped.")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping server: ${e.message}")
            }
        }
        serverSockets.clear()
        threadPool.shutdown()
    }

    private fun handleRequest(socket: Socket, serverPort: Int) {
        try {
            socket.soTimeout = 120000
            val input = BufferedReader(InputStreamReader(socket.inputStream, "UTF-8"))
            val output = socket.outputStream

            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendResponse(output, 400, "Bad Request", "text/plain")
                return
            }
            val method = parts[0]
            var fullPath = parts[1]

            // Handle absolute-form URLs: http://127.0.0.1:7860/path -> /path
            if (fullPath.startsWith("http://") || fullPath.startsWith("https://")) {
                try {
                    val absUri = java.net.URI(fullPath)
                    fullPath = absUri.rawPath + if (absUri.rawQuery != null) "?${absUri.rawQuery}" else ""
                } catch (_: Exception) {}
            }

            val headers = mutableMapOf<String, String>()
            var line: String?
            var contentLength = 0
            while (true) {
                line = input.readLine()
                if (line.isNullOrEmpty() || line == "\r") break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                    if (key == "content-length") contentLength = value.toIntOrNull() ?: 0
                }
            }

            if (method == "POST" && contentLength > 0) {
                val discard = CharArray(contentLength)
                input.read(discard, 0, contentLength)
            }

            val path = fullPath.substringBefore('?')
            val queryString = fullPath.substringAfter('?', "")

            val queryParams = mutableMapOf<String, String>()
            if (queryString.isNotEmpty()) {
                queryString.split("&").forEach { param ->
                    val eqIdx = param.indexOf('=')
                    if (eqIdx > 0) {
                        val key = URLDecoder.decode(param.substring(0, eqIdx), "UTF-8")
                        val value = URLDecoder.decode(param.substring(eqIdx + 1), "UTF-8")
                        queryParams[key] = value
                    }
                }
            }

            Log.d(TAG, "$method $path on port $serverPort (raw: $requestLine)")

            when {
                path == "/" -> {
                    sendResponse(output, 200, "OK", "text/plain")
                }
                path == "/proxy/manifest.m3u8" -> {
                    handleManifestProxy(output, queryParams)
                }
                path == "/proxy/segment" -> {
                    handleSegmentProxy(output, queryParams)
                }
                path == "/proxy/video" -> {
                    handleVideoProxy(output, queryParams, headers)
                }
                path.startsWith("/stream/") && path.endsWith(".json") -> {
                    val pathSegments = path.removeSuffix(".json").split("/")
                    val type = URLDecoder.decode(pathSegments.getOrElse(2) { "movie" }, "UTF-8")
                    val id = URLDecoder.decode(pathSegments.getOrElse(3) { "" }, "UTF-8")
                    val title = queryParams["title"]
                    handleStreamResolve(output, type, id, title)
                }
                else -> {
                    Log.e(TAG, "Unknown path: '$path' (raw req: '$requestLine')")
                    sendResponse(output, 404, "Not Found: $path", "text/plain")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request: ${e.message}", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, body: String, contentType: String = "text/plain") {
        try {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val header = "HTTP/1.1 $statusCode OK\r\n" +
                    "Content-Type: $contentType\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"
            output.write(header.toByteArray())
            output.write(bytes)
            output.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending response: ${e.message}")
        }
    }

    private fun sendBytesResponse(output: OutputStream, statusCode: Int, bytes: ByteArray, contentType: String) {
        try {
            val header = "HTTP/1.1 $statusCode OK\r\n" +
                    "Content-Type: $contentType\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"
            output.write(header.toByteArray())
            output.write(bytes)
            output.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending bytes response: ${e.message}")
        }
    }

    private fun handleManifestProxy(output: OutputStream, params: Map<String, String>) {
        val url = params["url"]
        val referer = params["h_Referer"] ?: "$SITE_URL/"
        val userAgent = params["h_UA"] ?: FIXED_USER_AGENT
        val origin = params["h_Origin"] ?: ""
        val cookie = params["h_Cookie"] ?: ""

        if (url == null) {
            sendResponse(output, 400, "Manifest URL parameter is missing.", "text/plain")
            return
        }

        val decodedUrl = URLDecoder.decode(url, "UTF-8")
        Log.d(TAG, "Fetching manifest from: $decodedUrl Referer: $referer UA: ${userAgent.take(30)}")

        try {
            val reqBuilder = Request.Builder()
                .url(decodedUrl)
                .header("Referer", referer)
                .header("User-Agent", userAgent)
            if (origin.isNotBlank()) {
                reqBuilder.header("Origin", origin)
            }
            if (cookie.isNotBlank()) {
                reqBuilder.header("Cookie", cookie)
            }
            val request = reqBuilder.get().build()

            val response = client.newCall(request).execute()
            val responseBody = response.body
            val bodyString = responseBody?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Origin server returned ${response.code} for $decodedUrl")
                sendResponse(output, response.code, "Origin error: ${response.code}", "text/plain")
                return
            }

            if (bodyString.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true) ||
                bodyString.trimStart().startsWith("<html", ignoreCase = true)) {
                Log.e(TAG, "Received HTML instead of M3U8. This means the scraper failed.")
                sendResponse(output, 404, "Il link non è un manifest video (scrapers failed)", "text/plain")
                return
            }

            val rewrittenManifest = rewriteManifest(bodyString, decodedUrl, referer, userAgent, origin, cookie)
            sendResponse(output, 200, rewrittenManifest, "application/vnd.apple.mpegurl")
            Log.d(TAG, "Manifest rewritten and served successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching manifest from $decodedUrl: ${e.message}", e)
            sendResponse(output, 500, e.message ?: "Error fetching manifest", "text/plain")
        }
    }

    private fun handleSegmentProxy(output: OutputStream, params: Map<String, String>) {
        val url = params["url"]
        val referer = params["h_Referer"] ?: "$SITE_URL/"
        val userAgent2 = params["h_UA"] ?: FIXED_USER_AGENT
        val origin2 = params["h_Origin"] ?: ""
        val cookie2 = params["h_Cookie"] ?: ""

        if (url == null) {
            sendResponse(output, 400, "Missing url", "text/plain")
            return
        }

        val decodedUrl = URLDecoder.decode(url, "UTF-8")
        Log.d(TAG, "Proxying segment: $decodedUrl")

        try {
            val requestBuilder = Request.Builder()
                .url(decodedUrl)
                .header("User-Agent", userAgent2)
                .header("Referer", referer)
            if (origin2.isNotBlank()) {
                requestBuilder.header("Origin", origin2)
            }
            if (cookie2.isNotBlank()) {
                requestBuilder.header("Cookie", cookie2)
            }
            requestBuilder.get()

            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body
            val bytes = responseBody?.bytes() ?: ByteArray(0)
            val bodyString = String(bytes, Charsets.UTF_8)

            if (bodyString.trimStart().startsWith("#EXTM3U")) {
                Log.d(TAG, "Segment is an M3U8 playlist, rewriting URLs")
                val rewritten = rewriteManifest(bodyString, decodedUrl, referer, userAgent2, origin2, cookie2)
                sendResponse(output, response.code, rewritten, "application/vnd.apple.mpegurl")
            } else {
                val contentType = responseBody?.contentType()?.toString() ?: "application/octet-stream"
                sendBytesResponse(output, response.code, bytes, contentType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error proxying segment: ${e.message}")
            sendResponse(output, 500, e.message ?: "Error proxying segment", "text/plain")
        }
    }

    private fun handleVideoProxy(output: OutputStream, params: Map<String, String>, requestHeaders: Map<String, String>) {
        val url = params["url"]
        val referer = params["h_Referer"] ?: "$SITE_URL/"
        val userAgent = params["h_UA"] ?: FIXED_USER_AGENT
        val origin = params["h_Origin"] ?: ""
        val cookie = params["h_Cookie"] ?: ""

        if (url == null) {
            sendResponse(output, 400, "Missing url", "text/plain")
            return
        }

        val decodedUrl = URLDecoder.decode(url, "UTF-8")
        Log.d(TAG, "Video proxy request: $decodedUrl")

        try {
            val reqBuilder = Request.Builder()
                .url(decodedUrl)
                .header("User-Agent", userAgent)
                .header("Referer", referer)
                .header("Accept", "*/*")
            if (origin.isNotBlank()) reqBuilder.header("Origin", origin)
            if (cookie.isNotBlank()) reqBuilder.header("Cookie", cookie)
            val range = requestHeaders["range"]
            if (!range.isNullOrBlank()) reqBuilder.header("Range", range)

            val response = client.newCall(reqBuilder.get().build()).execute()
            response.use { resp ->
                val body = resp.body
                if (body == null) {
                    sendResponse(output, 502, "Empty body", "text/plain")
                    return
                }

                val contentType = body.contentType()?.toString() ?: "application/octet-stream"
                val contentLength = body.contentLength()
                val contentRange = resp.header("Content-Range")

                val headerStr = StringBuilder()
                headerStr.append("HTTP/1.1 ${resp.code} ${reasonPhrase(resp.code)}\r\n")
                headerStr.append("Content-Type: $contentType\r\n")
                headerStr.append("Accept-Ranges: bytes\r\n")
                headerStr.append("Access-Control-Allow-Origin: *\r\n")
                if (contentRange != null) headerStr.append("Content-Range: $contentRange\r\n")
                if (contentLength >= 0) headerStr.append("Content-Length: $contentLength\r\n")
                headerStr.append("Connection: close\r\n\r\n")
                output.write(headerStr.toString().toByteArray(Charsets.UTF_8))

                val input = body.byteStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    total += read
                }
                output.flush()
                Log.d(TAG, "Video proxy streamed $total bytes from $decodedUrl")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video proxy error: ${e.message}")
        }
    }

    private fun reasonPhrase(code: Int): String = when (code) {
        200 -> "OK"
        206 -> "Partial Content"
        301 -> "Moved Permanently"
        302 -> "Found"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        416 -> "Range Not Satisfiable"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        else -> "OK"
    }

    private fun handleStreamResolve(output: OutputStream, type: String, id: String, title: String?) {
        try {
            altadefinizioneExpired = false
            val streams = resolveStreams(type, id, title)
            val json = gson.toJson(mapOf(
                "streams" to streams,
                "altadefinizioneExpired" to altadefinizioneExpired
            ))
            sendResponse(output, 200, json, "application/json")
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving streams: ${e.message}", e)
            sendResponse(output, 500, """{"streams":[]}""", "application/json")
        }
    }

    private fun resolveStreams(type: String, tmdbId: String, @Suppress("UNUSED_PARAMETER") title: String?): List<Map<String, String>> {
        val streams = mutableListOf<Map<String, String>>()
        Log.d(TAG, "Resolving streams for $type ID: $tmdbId, Title: $title")

        val parts = tmdbId.split(":")
        val id = if (parts.size > 1) parts[1] else tmdbId
        val season = if (parts.size > 2) parts[2] else "1"
        val episode = if (parts.size > 3) parts[3] else "1"

        val isMovie = (type == "movie")

        // ─── Provider 1: VixSrc ───────────────────────────────────
        val vixSrcStreams = resolveVixSrc(isMovie, id, season, episode)
        streams.addAll(vixSrcStreams)

        // ─── Provider 2: VidxGo ───────────────────────────────────
        val vidxGoStreams = resolveVidxGo(isMovie, id, season, episode)
        streams.addAll(vidxGoStreams)

        // ─── Provider 3: AltadefinizioneStreaming ────────────────
        val altadefStreams = resolveAltadefinizioneStreaming(isMovie, id, season, episode)
        streams.addAll(altadefStreams)

        Log.d(TAG, "Resolved ${streams.size} streams total (${vixSrcStreams.size} VixSrc, ${vidxGoStreams.size} VidxGo, ${altadefStreams.size} Altadefinizione)")
        return streams
    }

    // ─── VixSrc resolver (existing vixsrc.to) ────────────────────

    private fun resolveVixSrc(isMovie: Boolean, tmdbId: String, season: String, episode: String): List<Map<String, String>> {
        val streams = mutableListOf<Map<String, String>>()

        val apiUrl = if (isMovie) {
            "$SITE_URL/api/movie/$tmdbId"
        } else {
            "$SITE_URL/api/tv/$tmdbId/$season/$episode"
        }

        try {
            Log.d(TAG, "Resolving VixSrc via API: $apiUrl")
            val apiRequest = Request.Builder()
                .url(apiUrl)
                .header("Referer", "$SITE_URL/")
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .get()
                .build()

            val apiResponse = client.newCall(apiRequest).execute()
            if (apiResponse.isSuccessful) {
                val apiJson = apiResponse.body?.string() ?: ""
                Log.d(TAG, "VixSrc API response: ${apiJson.take(300)}")
                val apiData = gson.fromJson(apiJson, Map::class.java)
                val embedPath = apiData["src"] as? String

                if (embedPath != null) {
                    val embedUrl = if (embedPath.startsWith("http")) embedPath else "$SITE_URL$embedPath"
                    Log.d(TAG, "Found VixSrc embed URL: $embedUrl")

                    val realVideoUrl = scrapeVixStream(embedUrl)
                    if (realVideoUrl != null) {
                        streams.add(mapOf(
                            "name" to "VixSrc",
                            "title" to "ITA | Full HD | Direct",
                            "url" to "http://127.0.0.1:$actualPrimaryPort/proxy/manifest.m3u8?url=" +
                                    URLEncoder.encode(realVideoUrl, "UTF-8") +
                                    "&h_Referer=" + URLEncoder.encode("$SITE_URL/", "UTF-8"),
                            "source" to "VixSrc",
                            "quality" to "1080p"
                        ))
                        Log.d(TAG, "VixSrc stream resolved successfully")
                    } else {
                        Log.e(TAG, "VixSrc: failed to scrape video URL from embed page")
                    }
                } else {
                    Log.e(TAG, "VixSrc: no src field in API response")
                }
            } else {
                Log.e(TAG, "VixSrc API failed: ${apiResponse.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "VixSrc resolver error: ${e.message}")
        }

        return streams
    }

    // ─── VidxGo resolver ─────────────────────────────────────────

    private fun resolveVidxGo(isMovie: Boolean, tmdbId: String, season: String, episode: String): List<Map<String, String>> {
        val streams = mutableListOf<Map<String, String>>()

        try {
            val imdbId = resolveImdbId(isMovie, tmdbId)
            if (imdbId == null) {
                Log.e(TAG, "VidxGo: could not resolve IMDB ID for TMDB $tmdbId")
                return streams
            }

            val vidxgoUrl = if (isMovie) {
                "https://v.vidxgo.co/$imdbId"
            } else {
                "https://v.vidxgo.co/$imdbId/$season/$episode"
            }
            Log.d(TAG, "VidxGo URL: $vidxgoUrl")

            val extracted = extractVidxGoStream(vidxgoUrl)
            if (extracted != null) {
                val (streamUrl, quality) = extracted
                val proxied = "http://127.0.0.1:$actualPrimaryPort/proxy/manifest.m3u8?url=" +
                        URLEncoder.encode(streamUrl, "UTF-8") +
                        "&h_Referer=" + URLEncoder.encode(vidxgoUrl, "UTF-8") +
                        "&h_UA=" + URLEncoder.encode(FIREFOX_UA, "UTF-8") +
                        "&h_Origin=" + URLEncoder.encode("https://v.vidxgo.co", "UTF-8")

                streams.add(mapOf(
                    "name" to "VidxGo",
                    "title" to "ITA | $quality | VidxGo",
                    "url" to proxied,
                    "source" to "VidxGo",
                    "quality" to quality
                ))
                Log.d(TAG, "VidxGo stream resolved: $quality")
            } else {
                Log.e(TAG, "VidxGo: extraction failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "VidxGo resolver error: ${e.message}")
        }

        return streams
    }

    // ─── AltadefinizioneStreaming resolver ────────────────────────

    private fun resolveAltadefinizioneStreaming(isMovie: Boolean, tmdbId: String, season: String, episode: String): List<Map<String, String>> {
        val streams = mutableListOf<Map<String, String>>()

        try {
            val cdnStreams = resolveAltadefinizioneCDN(isMovie, tmdbId, season, episode)
            streams.addAll(cdnStreams)

            val mixdropStreams = resolveAltadefinizioneMixDrop(isMovie, tmdbId, season, episode)
            streams.addAll(mixdropStreams)

            Log.d(TAG, "Altadefinizione: resolved ${streams.size} streams (${cdnStreams.size} CDN, ${mixdropStreams.size} MixDrop)")
        } catch (e: Exception) {
            Log.e(TAG, "Altadefinizione resolver error: ${e.message}")
        }

        return streams
    }

    private fun resolveAltadefinizioneCDN(isMovie: Boolean, tmdbId: String, season: String, episode: String): List<Map<String, String>> {
        val streams = mutableListOf<Map<String, String>>()

        try {
            val endpoint = if (isMovie) {
                "$ALTADEF_BASE/api/player-sources/movie/$tmdbId"
            } else {
                "$ALTADEF_BASE/api/player-sources/tv/$tmdbId/$season/$episode"
            }

            Log.d(TAG, "Altadefinizione CDN request: $endpoint")
            val req = Request.Builder()
                .url(endpoint)
                .header("User-Agent", FIXED_USER_AGENT)
                .header("Referer", "$ALTADEF_BASE/")
                .header("Accept", "application/json, text/plain, */*")
                .header("Cookie", getAltadefCookie())
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.e(TAG, "Altadefinizione CDN: response code ${resp.code}")
                return streams
            }

            val bodyStr = resp.body?.string() ?: return streams
            Log.d(TAG, "Altadefinizione CDN response: ${bodyStr.take(300)}")

            val root = gson.fromJson(bodyStr, Map::class.java)
            val playerGate = root["playerGate"] as? Map<*, *>
            if (playerGate != null) {
                val loginRequired = playerGate["loginRequired"] as? Boolean ?: false
                val allowed = playerGate["allowed"] as? Boolean ?: false
                val enabled = playerGate["enabled"] as? Boolean ?: false
                Log.e(TAG, "Altadefinizione playerGate: loginRequired=$loginRequired allowed=$allowed msg=${root["message"]}")
                if (loginRequired && !allowed) {
                    if (enabled || true) altadefinizioneExpired = true
                    return streams
                }
            }
            // Anche senza playerGate esplicito: gate telegram = sessione da rifare
            if (root["gate"] == "telegram") {
                altadefinizioneExpired = true
                return streams
            }
            val sources = root["sources"] as? List<*> ?: return streams

            for (src in sources) {
                val sourceMap = src as? Map<*, *> ?: continue
                val provider = sourceMap["provider"] as? String ?: ""
                val url = sourceMap["url"] as? String ?: ""
                val isCdn = provider.lowercase() == "cdn"
                val isVixSrc = url.contains("vixsrc.to", ignoreCase = true) || url.contains("tam-o", ignoreCase = true)
                Log.d(TAG, "Altadefinizione source: provider='$provider' hls=${isHlsUrl(url)} url=$url")
                if (url.isBlank() || isVixSrc) continue

                if (isCdn) {
                    val adefCookie = getAltadefCookie()
                    val playUrl = buildAltadefPlayUrl(url, adefCookie)
                    streams.add(mapOf(
                        "name" to "Altadefinizione CDN",
                        "title" to "ITA | CDN | Direct",
                        "url" to playUrl,
                        "source" to "AltadefinizioneStreaming",
                        "quality" to "720p"
                    ))
                    Log.d(TAG, "Altadefinizione CDN stream added (hls=${isHlsUrl(url)}): $url")
                    break
                }
            }

            if (streams.isEmpty() && sources.isNotEmpty()) {
                for (src in sources) {
                    val sourceMap = src as? Map<*, *> ?: continue
                    val url = sourceMap["url"] as? String ?: continue
                    if (url.isBlank() || url.contains("vixsrc.to", ignoreCase = true) || url.contains("tam-o", ignoreCase = true)) continue
                    if (!looksLikeVideoUrl(url)) continue
                    val adefCookie = getAltadefCookie()
                    val playUrl = buildAltadefPlayUrl(url, adefCookie)
                    streams.add(mapOf(
                        "name" to "Altadefinizione",
                        "title" to "ITA | Direct",
                        "url" to playUrl,
                        "source" to "AltadefinizioneStreaming",
                        "quality" to "720p"
                    ))
                    Log.d(TAG, "Altadefinizione fallback stream added (hls=${isHlsUrl(url)}): $url")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Altadefinizione CDN error: ${e.message}")
        }

        return streams
    }

    private fun resolveAltadefinizioneMixDrop(isMovie: Boolean, tmdbId: String, season: String, episode: String): List<Map<String, String>> {
        val streams = mutableListOf<Map<String, String>>()

        try {
            val downloadEndpoint = if (isMovie) {
                "$ALTADEF_BASE/api/download/$tmdbId"
            } else {
                "$ALTADEF_BASE/api/download-episodes/$tmdbId"
            }

            Log.d(TAG, "Altadefinizione MixDrop download request: $downloadEndpoint")
            val downloadReq = Request.Builder()
                .url(downloadEndpoint)
                .header("User-Agent", FIXED_USER_AGENT)
                .header("Referer", "$ALTADEF_BASE/")
                .header("Accept", "application/json, text/plain, */*")
                .header("Cookie", getAltadefCookie())
                .get()
                .build()

            val downloadResp = client.newCall(downloadReq).execute()
            if (!downloadResp.isSuccessful) {
                Log.e(TAG, "Altadefinizione MixDrop download: response code ${downloadResp.code}")
                return streams
            }

            val bodyStr = downloadResp.body?.string() ?: return streams
            val root = gson.fromJson(bodyStr, Map::class.java)

            val available = root["available"] as? Boolean ?: false
            if (!available) {
                Log.d(TAG, "Altadefinizione MixDrop: download not available")
                return streams
            }

            var downloadUrl: String? = null
            if (isMovie) {
                downloadUrl = root["url"] as? String
            } else {
                val episodes = root["episodes"] as? List<*>
                val sNum = season.toIntOrNull() ?: 1
                val eNum = episode.toIntOrNull() ?: 1
                for (ep in episodes ?: emptyList<Any>()) {
                    val epMap = ep as? Map<*, *> ?: continue
                    val epSeason = (epMap["season"] as? Number)?.toInt()
                    val epEpisode = (epMap["episode"] as? Number)?.toInt()
                    if (epSeason == sNum && epEpisode == eNum) {
                        downloadUrl = epMap["url"] as? String
                        break
                    }
                }
            }

            if (downloadUrl == null) {
                Log.d(TAG, "Altadefinizione MixDrop: no download URL found")
                return streams
            }

            val absoluteDlUrl = if (downloadUrl.startsWith("http")) downloadUrl else "$ALTADEF_BASE$downloadUrl"
            val separator = if (absoluteDlUrl.contains("?")) "&" else "?"
            val withGo = "$absoluteDlUrl${separator}go=1"

            Log.d(TAG, "Altadefinizione MixDrop following: $withGo")
            val redirectReq = Request.Builder()
                .url(withGo)
                .header("User-Agent", FIXED_USER_AGENT)
                .header("Referer", "$ALTADEF_BASE/")
                .header("Cookie", getAltadefCookie())
                .get()
                .build()

            val redirectResp = client.newCall(redirectReq).execute()
            val finalUrl = redirectResp.request.url.toString().replace(Regex("\\?download$", RegexOption.IGNORE_CASE), "")
            redirectResp.close()

            Log.d(TAG, "Altadefinizione MixDrop redirect final: $finalUrl")

            if (Regex("mixdrop|m1xdrop|mxdrop", RegexOption.IGNORE_CASE).containsMatchIn(finalUrl)) {
                val mixdropStream = extractMixDropStream(finalUrl)
                if (mixdropStream != null) {
                    val proxiedUrl = "http://127.0.0.1:$actualPrimaryPort/proxy/manifest.m3u8?url=" +
                            URLEncoder.encode(mixdropStream, "UTF-8") +
                            "&h_Referer=" + URLEncoder.encode(finalUrl, "UTF-8") +
                            "&h_Cookie=" + URLEncoder.encode(getAltadefCookie(), "UTF-8")
                    streams.add(mapOf(
                        "name" to "Altadefinizione MixDrop",
                        "title" to "ITA | MixDrop | 720p",
                        "url" to proxiedUrl,
                        "source" to "AltadefinizioneStreaming",
                        "quality" to "720p"
                    ))
                    Log.d(TAG, "Altadefinizione MixDrop stream added")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Altadefinizione MixDrop error: ${e.message}")
        }

        return streams
    }

    private fun extractMixDropStream(url: String): String? {
        try {
            Log.d(TAG, "Extracting MixDrop stream from: $url")
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", FIXED_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return null

            val m3u8Patterns = listOf(
                Regex("""["'](https?:[^"']+?\.m3u8[^"']*)["']"""),
                Regex("""src:\s*["'](https?:[^"']+?\.m3u8[^"']*)["']"""),
                Regex("""file:\s*["'](https?:[^"']+?\.m3u8[^"']*)["']""")
            )

            for (pattern in m3u8Patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val streamUrl = match.groupValues[1].replace("\\", "")
                    Log.d(TAG, "MixDrop stream extracted: $streamUrl")
                    return streamUrl
                }
            }

            Log.e(TAG, "MixDrop: no M3U8 found in page")
        } catch (e: Exception) {
            Log.e(TAG, "MixDrop extraction error: ${e.message}")
        }
        return null
    }

    private fun resolveImdbId(isMovie: Boolean, tmdbId: String): String? {
        val key = tmdbApiKey.ifBlank { return null }
        try {
            val endpoint = if (isMovie) "movie" else "tv"
            val url = "https://api.themoviedb.org/3/$endpoint/$tmdbId?api_key=$key"
            val req = Request.Builder().url(url).header("Accept", "application/json").get().build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: return null
                val root = gson.fromJson(body, Map::class.java)
                val imdbId = root["imdb_id"] as? String
                if (!imdbId.isNullOrBlank() && imdbId.startsWith("tt")) {
                    Log.d(TAG, "IMDB ID resolved from TMDB: $imdbId")
                    return imdbId
                }
            }
            // Fallback to external_ids
            val extUrl = "https://api.themoviedb.org/3/$endpoint/$tmdbId/external_ids?api_key=$key"
            val extReq = Request.Builder().url(extUrl).header("Accept", "application/json").get().build()
            val extResp = client.newCall(extReq).execute()
            if (extResp.isSuccessful) {
                val extBody = extResp.body?.string() ?: return null
                val extRoot = gson.fromJson(extBody, Map::class.java)
                val extImdbId = extRoot["imdb_id"] as? String
                if (!extImdbId.isNullOrBlank() && extImdbId.startsWith("tt")) {
                    Log.d(TAG, "IMDB ID resolved from external_ids: $extImdbId")
                    return extImdbId
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "IMDB resolve error: ${e.message}")
        }
        return null
    }

    private fun extractVidxGoStream(vidxgoUrl: String): Pair<String, String>? {
        try {
            Log.d(TAG, "Fetching VidxGo page: $vidxgoUrl")
            val req = Request.Builder()
                .url(vidxgoUrl)
                .header("User-Agent", FIREFOX_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-GPC", "1")
                .header("DNT", "1")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "iframe")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Referer", "https://altadefinizione.you/")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.e(TAG, "VidxGo page fetch failed: ${resp.code}")
                return null
            }
            val html = resp.body?.string() ?: return null

            // Check for corrupt player
            if (Regex("player-container[^>]*\\bcorrupt\\b", RegexOption.IGNORE_CASE).containsMatchIn(html)) {
                Log.e(TAG, "VidxGo: source marked as corrupt")
                return null
            }

            // ── Strategy 1: XOR-encrypted blocks ──────────────────
            // var X = 'key', d = atob('base64') — single line
            val xorPattern1 = Regex("""var\s+\w+\s*=\s*'([^']+)'\s*,?\s*\w+\s*=\s*atob\s*\(\s*'([A-Za-z0-9+/=]+)'\s*\)""")
            // Separate var declarations: var X = 'key'; var Y = atob('base64')
            val xorPattern2 = Regex("""var\s+\w+\s*=\s*'([^']+)';?\s*\n?\s*var\s+\w+\s*=\s*atob\s*\(\s*'([A-Za-z0-9+/=]+)'\s*\)""")

            val xorPatterns = listOf(xorPattern1, xorPattern2)

            for (pattern in xorPatterns) {
                val matches = pattern.findAll(html)
                for (match in matches) {
                    try {
                        val key = match.groupValues[1]
                        val b64 = match.groupValues[2]
                        val decrypted = xorDecrypt(b64, key)
                        Log.d(TAG, "VidxGo XOR decrypted (${decrypted.length} bytes): ${decrypted.take(200)}")

                        val m3u8Patterns = listOf(
                            Regex("""currentSrc\s*=\s*["'](https?:[^"']+?\.m3u8[^"']*)["']"""),
                            Regex("""src\s*=\s*["'](https?:[^"']+?\.m3u8[^"']*)["']"""),
                            Regex("""file:\s*["'](https?:[^"']+?\.m3u8[^"']*)["']"""),
                            Regex("""source:\s*["'](https?:[^"']+?\.m3u8[^"']*)["']""")
                        )
                        for (m3u8Pattern in m3u8Patterns) {
                            val streamMatch = m3u8Pattern.find(decrypted)
                            if (streamMatch != null) {
                                val streamUrl = streamMatch.groupValues[1].replace("\\", "")
                                val quality = detectQualityFromUrl(streamUrl)
                                Log.d(TAG, "VidxGo decrypted stream URL: $streamUrl (quality=$quality)")
                                return Pair(streamUrl, quality)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "VidxGo XOR decrypt attempt failed: ${e.message}")
                        continue
                    }
                }
            }

            // ── Strategy 2: iframe src extraction ─────────────────
            val iframeMatch = Regex("""<iframe[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
            if (iframeMatch != null) {
                val iframeSrc = iframeMatch.groupValues[1]
                val iframeUrl = if (iframeSrc.startsWith("http")) iframeSrc else {
                    val base = java.net.URI(vidxgoUrl)
                    base.resolve(iframeSrc).toString()
                }
                Log.d(TAG, "VidxGo: following iframe: $iframeUrl")
                val iframeResult = extractVidxGoStream(iframeUrl)
                if (iframeResult != null) return iframeResult
            }

            // ── Strategy 3: direct M3U8 in page ────────────────────
            val directM3u8 = Regex("""["'](https?:[^"']+?\.m3u8[^"']*)["']""").find(html)
            if (directM3u8 != null) {
                val streamUrl = directM3u8.groupValues[1].replace("\\", "")
                val quality = detectQualityFromUrl(streamUrl)
                Log.d(TAG, "VidxGo: found direct m3u8 URL in page")
                return Pair(streamUrl, quality)
            }

            Log.e(TAG, "VidxGo: no stream URL found in page (${html.length} bytes)")
            Log.e(TAG, "VidxGo HTML first 500: ${html.take(500)}")
        } catch (e: Exception) {
            Log.e(TAG, "VidxGo extraction error: ${e.message}")
        }
        return null
    }

    private fun xorDecrypt(b64: String, key: String): String {
        val decoded = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        val result = ByteArray(decoded.size)
        for (i in decoded.indices) {
            result[i] = (decoded[i].toInt() xor key[i % key.length].code).toByte()
        }
        return String(result, Charsets.UTF_8)
    }

    private fun detectQualityFromUrl(url: String): String {
        val urlPath = url.lowercase().substringBefore("?")
        return when {
            urlPath.contains("4k") || urlPath.contains("2160") -> "4K"
            urlPath.contains("1440") || urlPath.contains("2k") -> "1440p"
            urlPath.contains("1080") || urlPath.contains("fhd") -> "1080p"
            urlPath.contains("720") || urlPath.contains("hd") -> "720p"
            urlPath.contains("480") || urlPath.contains("sd") -> "480p"
            urlPath.contains("360") -> "360p"
            else -> "HD"
        }
    }

    private fun scrapeVixStream(url: String): String? {
        return try {
            Log.d(TAG, "Starting SCRAPE for $url")
            val request = Request.Builder()
                .url(url)
                .header("Referer", "$SITE_URL/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Scraper failed to load page: ${response.code}")
                return null
            }
            val html = response.body?.string() ?: return null

            if (html.contains("Just a moment...") || html.contains("cloudflare")) {
                Log.e(TAG, "Scraper blocked by Cloudflare challenge")
                return null
            }

            val token = Regex("'token':\\s*'([a-f0-9]+)'").find(html)?.groupValues?.get(1)
                ?: Regex("\"token\":\\s*\"([a-f0-9]+)\"").find(html)?.groupValues?.get(1)
            val expires = Regex("'expires':\\s*'(\\d+)'").find(html)?.groupValues?.get(1)
                ?: Regex("\"expires\":\\s*\"(\\d+)\"").find(html)?.groupValues?.get(1)

            if (token == null || expires == null) {
                Log.e(TAG, "Token/expires not found in page")
                return null
            }

            // Strategy 1: window.streams — prefer 1080p / highest quality
            val streamsBlock = findStreamsArray(html)
            if (streamsBlock != null) {
                val bestUrl = extractBestStream(streamsBlock, token, expires)
                if (bestUrl != null) {
                    Log.d(TAG, "Using stream from window.streams block")
                    return bestUrl
                }
            }

            // Strategy 2: masterPlaylist URL
            val videoUrl = Regex("url:\\s*'([^']+)'").find(html)?.groupValues?.get(1)
                ?: Regex("\"url\":\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            if (videoUrl != null) {
                val finalUrl = buildProxiedUrl(videoUrl.replace("\\/", "/"), token, expires)
                Log.d(TAG, "Using masterPlaylist URL: $finalUrl")
                return finalUrl
            }

            Log.e(TAG, "All extraction strategies failed")
            Log.e(TAG, "HTML snippet (first 1000 chars): ${html.take(1000)}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error during scraping: ${e.message}")
            null
        }
    }

    private fun findStreamsArray(html: String): String? {
        val prefix = "window.streams"
        val idx = html.indexOf(prefix)
        if (idx < 0) return null
        val bracketStart = html.indexOf('[', idx)
        if (bracketStart < 0) return null
        var depth = 0
        for (i in bracketStart until html.length) {
            when (html[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return html.substring(bracketStart, i + 1)
                }
            }
        }
        return null
    }

    private fun extractBestStream(streamsJson: String, token: String, expires: String): String? {
        val entries = Regex("""\{[^}]*\}""").findAll(streamsJson)
        var bestUrl: String? = null
        var bestScore = -1
        var anyUrl: String? = null

        for (entry in entries) {
            val entryStr = entry.value
            val streamUrl = Regex("\"url\":\\s*\"([^\"]+)\"").find(entryStr)?.groupValues?.get(1)
                ?: Regex("'url':\\s*'([^']+)'").find(entryStr)?.groupValues?.get(1)
            if (streamUrl == null) continue

            val qualityLabel = entryStr.lowercase()
            val score = when {
                qualityLabel.contains("1080") || qualityLabel.contains("fhd") || qualityLabel.contains("full hd") -> 3
                qualityLabel.contains("720") || qualityLabel.contains("hd") -> 2
                else -> 1
            }

            val isActive = entryStr.contains("\"active\":true") || entryStr.contains("'active':true")

            val cleanUrl = streamUrl.replace("\\/", "/")
            if (isActive && anyUrl == null) anyUrl = cleanUrl

            if (score > bestScore) {
                bestScore = score
                bestUrl = cleanUrl
            }
        }

        val chosenUrl = bestUrl ?: anyUrl
        if (chosenUrl != null) {
            val finalUrl = buildProxiedUrl(chosenUrl, token, expires)
            Log.d(TAG, "Best stream (score=$bestScore): $finalUrl")
            return finalUrl
        }
        return null
    }

    private fun buildProxiedUrl(videoUrl: String, token: String, expires: String): String {
        var url = videoUrl

        if (url.contains("/playlist/") && !url.contains(".m3u8")) {
            url = url.substringBefore("?") + ".m3u8"
        }

        // Already has token/expires in URL, just add h=1
        if (url.contains("token=") && url.contains("expires=")) {
            val separator = if (url.contains("?")) "&" else "?"
            val finalUrl = "$url${separator}h=1"
            Log.d(TAG, "VixSrc URL (token already present): $finalUrl")
            return finalUrl
        }

        val separator = if (url.contains("?")) "&" else "?"
        val finalUrl = "$url${separator}token=$token&expires=$expires&h=1"
        Log.d(TAG, "VixSrc URL built with FHD enabled: $finalUrl")
        return finalUrl
    }

    private fun rewriteManifest(content: String, baseUrl: String, referer: String, userAgent: String = FIXED_USER_AGENT, origin: String = "", cookie: String = ""): String {
        val lines = content.lines()
        val rewritten = StringBuilder()
        val baseUri = java.net.URI(baseUrl)

        fun resolveUri(uri: String): String {
            if (uri.startsWith("http")) return uri
            return baseUri.resolve(uri).toString()
        }

        fun proxyUri(uri: String): String {
            val absolute = resolveUri(uri)
            val isManifest = absolute.contains(".m3u8")
            val endpoint = if (isManifest) "/proxy/manifest.m3u8" else "/proxy/segment"
            val params = mutableListOf("url=" + URLEncoder.encode(absolute, "UTF-8"),
                "h_Referer=" + URLEncoder.encode(referer, "UTF-8"))
            if (userAgent != FIXED_USER_AGENT) {
                params.add("h_UA=" + URLEncoder.encode(userAgent, "UTF-8"))
            }
            if (origin.isNotBlank()) {
                params.add("h_Origin=" + URLEncoder.encode(origin, "UTF-8"))
            }
            if (cookie.isNotBlank()) {
                params.add("h_Cookie=" + URLEncoder.encode(cookie, "UTF-8"))
            }
            return "http://127.0.0.1:$actualPrimaryPort$endpoint?${params.joinToString("&")}"
        }

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> rewritten.append("\n")
                trimmed.startsWith("#") -> {
                    if (trimmed.startsWith("#EXT-X-MEDIA") || trimmed.startsWith("#EXT-X-KEY")) {
                        val uriRegex = Regex("URI=\"([^\"]*)\"")
                        val match = uriRegex.find(trimmed)
                        if (match != null) {
                            val originalUri = match.groupValues[1]
                            rewritten.append(line.replace(originalUri, proxyUri(originalUri))).append("\n")
                        } else {
                            rewritten.append(line).append("\n")
                        }
                    } else {
                        rewritten.append(line).append("\n")
                    }
                }
                else -> {
                    rewritten.append(proxyUri(trimmed)).append("\n")
                }
            }
        }
        return rewritten.toString()
    }
}

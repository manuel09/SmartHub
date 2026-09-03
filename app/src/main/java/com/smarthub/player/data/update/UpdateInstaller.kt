package com.smarthub.player.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object UpdateInstaller {
    private const val TAG = "UpdateInstaller"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun downloadAndInstall(context: Context, info: UpdateInfo): Result<Unit> = withContext(Dispatchers.IO) {
        val apkFile = File(context.cacheDir, "update_${info.versionCode}.apk")
        return@withContext try {
            val request = Request.Builder().url(info.apkUrl).get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                }
                val body = resp.body ?: return@withContext Result.failure(Exception("Empty body"))
                val total = resp.headers["Content-Length"]?.toLongOrNull() ?: 0L
                var downloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var lastProgressLog = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastProgressLog >= 2_000_000) {
                                lastProgressLog = downloaded
                                Log.d(TAG, "Downloaded $downloaded / $total bytes")
                            }
                        }
                    }
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }

            Log.d(TAG, "APK downloaded and installer opened: $apkFile")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Download/install error: ${e.message}")
            apkFile.delete()
            Result.failure(e)
        }
    }
}

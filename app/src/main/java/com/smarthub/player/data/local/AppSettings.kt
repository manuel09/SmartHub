package com.smarthub.player.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AppSettings {
    private const val SETTINGS_FILE = "app_settings.json"
    private val gson = Gson()

    private fun getFile(context: Context): java.io.File =
        java.io.File(context.filesDir, SETTINGS_FILE)

    private fun readMap(context: Context): MutableMap<String, Any> {
        val file = getFile(context)
        if (!file.exists()) return mutableMapOf()
        val json = file.readText()
        if (json.isBlank()) return mutableMapOf()
        val type = object : TypeToken<MutableMap<String, Any>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) { mutableMapOf() }
    }

    private fun writeMap(context: Context, map: Map<String, Any>) {
        getFile(context).writeText(gson.toJson(map))
    }

    var autoPlayNextEpisode: Boolean
        get() = false // default handled below
        set(value) = throw UnsupportedOperationException("Use context-based getter/setter")

    fun getAutoPlayNextEpisode(context: Context): Boolean {
        val map = readMap(context)
        return (map["autoPlayNextEpisode"] as? Boolean) ?: true
    }

    fun setAutoPlayNextEpisode(context: Context, enabled: Boolean) {
        val map = readMap(context)
        map["autoPlayNextEpisode"] = enabled
        writeMap(context, map)
    }

    fun getActiveProfileId(context: Context): String? {
        val map = readMap(context)
        val id = map["activeProfileId"] as? String
        return if (id.isNullOrBlank()) null else id
    }

    fun setActiveProfileId(context: Context, id: String) {
        val map = readMap(context)
        if (id.isBlank()) {
            map.remove("activeProfileId")
        } else {
            map["activeProfileId"] = id
        }
        writeMap(context, map)
    }

    fun getAppVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }
    }

    fun getAppVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) { 1L }
    }

    fun getAltadefinizioneCookie(context: Context): String {
        val map = readMap(context)
        return (map["altadefinizioneCookie"] as? String) ?: ""
    }

    fun setAltadefinizioneCookie(context: Context, cookie: String) {
        val map = readMap(context)
        map["altadefinizioneCookie"] = cookie
        writeMap(context, map)
    }
}

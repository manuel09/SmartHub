package com.smarthub.player.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object LocalStorage {
    private val gson = Gson()

    private fun fileName(base: String, context: Context, profileId: String?): String {
        val pid = profileId ?: ProfilesManager.getActiveProfileId(context) ?: "default"
        return "${pid}_${base}"
    }

    private fun getFile(context: Context, name: String): File =
        File(context.filesDir, name)

    // ─── Continue Watching ───────────────────────────────────────

    fun getContinueWatching(context: Context, profileId: String? = null): List<ContinueWatchingItem> {
        val file = getFile(context, fileName("cw.json", context, profileId))
        if (!file.exists()) return emptyList()
        val json = file.readText()
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<ContinueWatchingItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun saveContinueWatching(context: Context, item: ContinueWatchingItem, profileId: String? = null): List<ContinueWatchingItem> {
        val list = getContinueWatching(context, profileId).toMutableList()
        val existing = list.indexOfFirst { it.movieId == item.movieId && it.type == item.type }
        if (existing >= 0) {
            list[existing] = item.copy(timestamp = System.currentTimeMillis())
        } else {
            list.add(0, item)
        }
        list.sortByDescending { it.timestamp }
        if (list.size > 30) list.removeAt(list.lastIndex)
        val json = gson.toJson(list)
        getFile(context, fileName("cw.json", context, profileId)).writeText(json)
        return list
    }

    fun removeContinueWatching(context: Context, movieId: Int, type: String, profileId: String? = null): List<ContinueWatchingItem> {
        val list = getContinueWatching(context, profileId).toMutableList()
        list.removeAll { it.movieId == movieId && it.type == type }
        getFile(context, fileName("cw.json", context, profileId)).writeText(gson.toJson(list))
        return list
    }

    fun clearAllContinueWatching(context: Context, profileId: String? = null) {
        val file = getFile(context, fileName("cw.json", context, profileId))
        if (file.exists()) file.delete()
    }

    // ─── Favorites ────────────────────────────────────────────────

    fun getFavorites(context: Context, profileId: String? = null): List<FavoriteItem> {
        val file = getFile(context, fileName("fav.json", context, profileId))
        if (!file.exists()) return emptyList()
        val json = file.readText()
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<FavoriteItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun isFavorite(context: Context, movieId: Int, type: String, profileId: String? = null): Boolean {
        return getFavorites(context, profileId).any { it.movieId == movieId && it.type == type }
    }

    data class ToggleResult(val favorites: List<FavoriteItem>, val added: Boolean)

    fun toggleFavorite(context: Context, item: FavoriteItem, profileId: String? = null): ToggleResult {
        val list = getFavorites(context, profileId).toMutableList()
        val existing = list.indexOfFirst { it.movieId == item.movieId && it.type == item.type }
        val added: Boolean
        if (existing >= 0) {
            list.removeAt(existing)
            added = false
        } else {
            list.add(0, item)
            added = true
        }
        getFile(context, fileName("fav.json", context, profileId)).writeText(gson.toJson(list))
        return ToggleResult(list, added)
    }

    fun clearAllFavorites(context: Context, profileId: String? = null) {
        val file = getFile(context, fileName("fav.json", context, profileId))
        if (file.exists()) file.delete()
    }

    // ─── Search History ───────────────────────────────────────────

    fun getSearchHistory(context: Context, profileId: String? = null): List<SearchHistoryItem> {
        val file = getFile(context, fileName("sh.json", context, profileId))
        if (!file.exists()) return emptyList()
        val json = file.readText()
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<SearchHistoryItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun saveSearchQuery(context: Context, query: String, profileId: String? = null): List<SearchHistoryItem> {
        val list = getSearchHistory(context, profileId).toMutableList()
        list.removeAll { it.query.equals(query, ignoreCase = true) }
        list.add(0, SearchHistoryItem(query))
        if (list.size > 20) list.removeAt(list.lastIndex)
        getFile(context, fileName("sh.json", context, profileId)).writeText(gson.toJson(list))
        return list
    }

    fun removeSearchQuery(context: Context, query: String, profileId: String? = null): List<SearchHistoryItem> {
        val list = getSearchHistory(context, profileId).toMutableList()
        list.removeAll { it.query.equals(query, ignoreCase = true) }
        getFile(context, fileName("sh.json", context, profileId)).writeText(gson.toJson(list))
        return list
    }

    fun clearSearchHistory(context: Context, profileId: String? = null) {
        val file = getFile(context, fileName("sh.json", context, profileId))
        if (file.exists()) file.delete()
    }
}

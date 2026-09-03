package com.smarthub.player.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object ProfilesManager {
    private const val PROFILES_FILE = "profiles.json"
    private val gson = Gson()

    private fun getFile(context: Context): java.io.File =
        java.io.File(context.filesDir, PROFILES_FILE)

    fun getAllProfiles(context: Context): List<UserProfile> {
        val file = getFile(context)
        if (!file.exists()) {
            val legacy = UserStorage.getUser(context)
            if (legacy != null) {
                val list = listOf(legacy)
                file.writeText(gson.toJson(list))
                migrateLegacyData(context, legacy.id)
                return list
            }
            return emptyList()
        }
        val json = file.readText()
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<UserProfile>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun migrateLegacyData(context: Context, profileId: String) {
        val filesDir = context.filesDir
        // Migrate continue watching
        val oldCw = File(filesDir, "continue_watching.json")
        if (oldCw.exists()) {
            val newCw = File(filesDir, "${profileId}_cw.json")
            oldCw.renameTo(newCw)
        }
        // Migrate favorites
        val oldFav = File(filesDir, "favorites.json")
        if (oldFav.exists()) {
            val newFav = File(filesDir, "${profileId}_fav.json")
            oldFav.renameTo(newFav)
        }
        // Migrate search history
        val oldSh = File(filesDir, "search_history.json")
        if (oldSh.exists()) {
            val newSh = File(filesDir, "${profileId}_sh.json")
            oldSh.renameTo(newSh)
        }
    }

    fun getProfile(context: Context, profileId: String): UserProfile? {
        return getAllProfiles(context).find { it.id == profileId }
    }

    fun saveProfile(context: Context, profile: UserProfile): List<UserProfile> {
        val list = getAllProfiles(context).toMutableList()
        val existing = list.indexOfFirst { it.id == profile.id }
        if (existing >= 0) {
            list[existing] = profile
        } else {
            list.add(profile)
        }
        getFile(context).writeText(gson.toJson(list))
        if (getActiveProfileId(context) == null) {
            setActiveProfile(context, profile.id)
        }
        return list
    }

    fun deleteProfile(context: Context, profileId: String): List<UserProfile> {
        val list = getAllProfiles(context).toMutableList()
        list.removeAll { it.id == profileId }
        getFile(context).writeText(gson.toJson(list))
        // Remove profile data files
        File(context.filesDir, "${profileId}_cw.json").delete()
        File(context.filesDir, "${profileId}_fav.json").delete()
        File(context.filesDir, "${profileId}_sh.json").delete()
        if (getActiveProfileId(context) == profileId) {
            clearActiveProfile(context)
        }
        return list
    }

    fun getActiveProfileId(context: Context): String? {
        return AppSettings.getActiveProfileId(context)
    }

    fun setActiveProfile(context: Context, profileId: String) {
        AppSettings.setActiveProfileId(context, profileId)
    }

    private fun clearActiveProfile(context: Context) {
        AppSettings.setActiveProfileId(context, "")
    }

    fun getActiveProfile(context: Context): UserProfile? {
        val id = getActiveProfileId(context) ?: return null
        return getProfile(context, id)
    }
}

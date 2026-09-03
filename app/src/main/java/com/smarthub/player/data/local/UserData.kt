package com.smarthub.player.data.local

import android.content.Context
import androidx.compose.runtime.Immutable
import com.google.gson.Gson

@Immutable
data class UserProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val email: String = "",
    val avatarColor: Long = 0xFFE50914
) {
    val initials: String
        get() = name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
}

object UserStorage {
    private const val USER_FILE = "user_profile.json"
    private val gson = Gson()

    private fun getFile(context: Context): java.io.File =
        java.io.File(context.filesDir, USER_FILE)

    fun getUser(context: Context): UserProfile? {
        val file = getFile(context)
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), UserProfile::class.java)
        } catch (e: Exception) { null }
    }

    fun saveUser(context: Context, user: UserProfile) {
        getFile(context).writeText(gson.toJson(user))
    }

    fun clearUser(context: Context) {
        val file = getFile(context)
        if (file.exists()) file.delete()
    }
}

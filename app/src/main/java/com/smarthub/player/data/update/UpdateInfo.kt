package com.smarthub.player.data.update

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val changelog: String = "",
    val mandatory: Boolean = false
)

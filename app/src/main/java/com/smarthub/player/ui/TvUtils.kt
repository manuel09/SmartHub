package com.smarthub.player.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object TvUtils {
    private var _isTelevision: Boolean? = null

    fun isTelevision(context: Context): Boolean {
        if (_isTelevision == null) {
            val um = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val uiModeTv = um?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            _isTelevision = uiModeTv || hasLeanback
        }
        return _isTelevision ?: false
    }
}

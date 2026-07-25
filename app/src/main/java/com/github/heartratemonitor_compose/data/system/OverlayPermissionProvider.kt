package com.github.heartratemonitor_compose.data.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 悬浮窗权限检查与系统设置跳转提供者。
 *
 * 将 [Settings.canDrawOverlays] 判断和权限 Intent 构造从 UI 层下沉到数据层，
 * 避免 Composable 直接操作系统 API。
 */
class OverlayPermissionProvider(context: Context) {

    private val applicationContext = context.applicationContext

    /**
     * 当前是否已获得悬浮窗权限。
     */
    fun canDrawOverlays(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(applicationContext)
    }

    /**
     * 构造前往系统悬浮窗权限设置页的 Intent。
     */
    fun createManageOverlayIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${applicationContext.packageName}")
        )
    }
}

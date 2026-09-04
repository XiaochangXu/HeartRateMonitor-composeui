package com.github.heartratemonitor_compose.data.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// open 化：单测以 Fake 子类替换（Robolectric 下系统权限判定不可控）。
@Singleton
open class OverlayPermissionProvider @Inject constructor(@ApplicationContext context: Context) {

    private val applicationContext = context.applicationContext

    open fun canDrawOverlays(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(applicationContext)
    }

    open fun createManageOverlayIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${applicationContext.packageName}")
        )
    }
}
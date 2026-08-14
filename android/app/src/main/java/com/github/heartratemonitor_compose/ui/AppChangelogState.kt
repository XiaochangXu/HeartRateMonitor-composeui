package com.github.heartratemonitor_compose.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository

/**
 * 更新日志状态管理。
 *
 * 检测首次安装或版本更新，自动触发更新日志 BottomSheet 弹出（仅一次）。
 */
@Stable
class AppChangelogState {
    var showChangelog by mutableStateOf(false)
    var changelogContent by mutableStateOf("")
    var changelogVersion by mutableStateOf("")
}

@Composable
fun rememberChangelogState(settings: SettingsRepository): AppChangelogState {
    val context = LocalContext.current
    val state = remember { AppChangelogState() }

    LaunchedEffect(Unit) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            val versionName = packageInfo.versionName?.removePrefix("v")?.removePrefix("V") ?: ""
            val lastShown = settings.getInt(PrefsKeys.CHANGELOG_LAST_SHOWN_VERSION, -1)
            if (lastShown != versionCode) {
                state.changelogContent = context.resources
                    .openRawResource(R.raw.changelog)
                    .bufferedReader()
                    .use { it.readText() }
                state.changelogVersion = versionName
                state.showChangelog = true
                settings.setInt(PrefsKeys.CHANGELOG_LAST_SHOWN_VERSION, versionCode)
            }
        } catch (_: Exception) {
            // 读取包信息失败时静默跳过
        }
    }

    return state
}

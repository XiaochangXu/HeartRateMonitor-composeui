package com.github.heartratemonitor_compose.ui

import android.content.Context
import android.os.Build
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class ChangelogNotice(
    val content: String,
    val versionName: String
)

/**
 * 纯 UDF 收敛：设置读写归本 Hilt 单例（契约 10），UI 只读收集 [notice]、
 * 经 [dismiss] 事件关闭；替代旧 rememberChangelogState 在 Composable
 * LaunchedEffect 内直读直写 SettingsRepository 的反模式。
 */
@Singleton
class ChangelogNotifier @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope
) {

    private val _notice = MutableStateFlow<ChangelogNotice?>(null)
    val notice: StateFlow<ChangelogNotice?> = _notice.asStateFlow()

    init {
        appScope.launch { checkAndMark() }
    }

    fun dismiss() {
        _notice.value = null
    }

    private fun checkAndMark() {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            val versionName = packageInfo.versionName?.removePrefix("v")?.removePrefix("V") ?: ""
            val lastShown = settings.get(SettingsKeys.CHANGELOG_LAST_SHOWN_VERSION)
            if (lastShown != versionCode) {
                val content = context.resources
                    .openRawResource(R.raw.changelog)
                    .bufferedReader()
                    .use { it.readText() }
                _notice.value = ChangelogNotice(content, versionName)
                settings.set(SettingsKeys.CHANGELOG_LAST_SHOWN_VERSION, versionCode)
            }
        } catch (_: Exception) {
        }
    }
}

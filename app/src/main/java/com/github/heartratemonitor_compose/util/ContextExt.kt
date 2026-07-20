package com.github.heartratemonitor_compose.util

import android.content.Context
import com.github.heartratemonitor_compose.HeartRateApp
import com.github.heartratemonitor_compose.data.repository.SettingsRepository

/**
 * 获取应用级 [SettingsRepository]。
 *
 * 通过 [Context.getApplicationContext] 访问 [HeartRateApp.settingsRepository]，
 * 避免在 Composable / ViewModel 中持有 Activity 上下文。
 */
val Context.settingsRepository: SettingsRepository
    get() = (applicationContext as HeartRateApp).settingsRepository

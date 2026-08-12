package com.github.heartratemonitor_compose.data.di

import android.content.Context
import com.github.heartratemonitor_compose.HeartRateApp
import com.github.heartratemonitor_compose.data.repository.SettingsRepository

/**
 * 通过 [Context] 获取 [AppContainer]。
 *
 * 适用于 Composable、ViewModel、Service 等持有上下文的位置，
 * 避免每个类单独维护单例构造逻辑。
 */
val Context.appContainer: AppContainer
    get() = (applicationContext as HeartRateApp).appContainer

/**
 * 通过 [Context] 获取应用级 [SettingsRepository]。
 *
 * 等价于 `appContainer.settingsRepository` 的快捷方式，
 * 供 Composable / ViewModel 在持有 Context 的位置直接获取设置仓库。
 */
val Context.settingsRepository: SettingsRepository
    get() = appContainer.settingsRepository

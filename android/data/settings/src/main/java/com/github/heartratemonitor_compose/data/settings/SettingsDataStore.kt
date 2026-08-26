package com.github.heartratemonitor_compose.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** 设置存储文件名，同时也是旧 SharedPreferences 文件名（作为迁移源）。 */
const val SETTINGS_FILE_NAME = "app_settings"

/**
 * 全应用唯一的设置 DataStore 实例。
 *
 * - 顶层属性委托保证全进程单实例（DataStore 官方硬性要求，多实例会抛 IllegalStateException）。
 * - 文件名沿用 [SETTINGS_FILE_NAME]，迁移源为同名 SharedPreferences：
 *   首次读取 `data` 时 [SharedPreferencesMigration] 将老用户数据无损迁入，
 *   并清空已迁移的 prefs 键；迁移幂等，后续读取自动跳过。
 * - 禁止组件自行构造 DataStore，统一经
 *   [SettingsRepository][com.github.heartratemonitor_compose.data.repository.SettingsRepository] 访问；
 *   唯一例外是进程死亡路径（KillStateSaver）与 ContentProvider 早期启动路径（ServiceBootInitializer），
 *   它们在 Hilt 组件就绪前/不可用时直连本实例（契约 2 例外）。
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_FILE_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, SETTINGS_FILE_NAME))
    }
)

package com.github.heartratemonitor_compose.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

const val SETTINGS_FILE_NAME = "app_settings"

// 全应用唯一 DataStore 实例；顶层属性委托保证全进程单实例（DataStore 硬性要求）。
// 经 SettingsRepository 访问；唯一例外：KillStateSaver / ServiceBootInitializer Hilt 未就绪时直连（契约 2 例外）。
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_FILE_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, SETTINGS_FILE_NAME))
    }
)

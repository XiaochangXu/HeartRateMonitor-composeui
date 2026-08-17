package com.github.heartratemonitor_compose.ui.theme

import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.google.common.truth.Truth.assertThat
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ThemeState] 主题配置状态测试。
 *
 * 验证配置快照从 SettingsRepository 预热加载、setter 即时更新 StateFlow 与
 * 乐观写回 DataStore（重新构造即"模拟重启"，持久化值生效）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemeStateTest {

    private lateinit var context: android.app.Application
    private lateinit var settings: SettingsRepository
    private lateinit var themeState: ThemeState

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SETTINGS_FILE_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking { context.settingsDataStore.edit { it.clear() } }
        settings = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))
        themeState = ThemeState(settings)
    }

    @Test
    fun `initial config reflects persisted defaults`() {
        val config = themeState.config.value
        assertThat(config.source).isEqualTo(ThemeSource.SYSTEM_MONET)
        assertThat(config.mode).isEqualTo(ThemeMode.FOLLOW_SYSTEM)
        assertThat(config.style).isEqualTo(PaletteStyle.TonalSpot)
    }

    @Test
    fun `setMode updates state flow and persists immediately`() {
        themeState.setMode(ThemeMode.DARK)
        assertThat(themeState.config.value.mode).isEqualTo(ThemeMode.DARK)
        // 乐观写回：同步读立即生效
        assertThat(settings.get(SettingsKeys.THEME_MODE)).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun `setSource toggles custom mode`() {
        themeState.setSource(ThemeSource.CUSTOM)
        assertThat(themeState.config.value.source).isEqualTo(ThemeSource.CUSTOM)
        assertThat(settings.get(SettingsKeys.THEME_SOURCE)).isEqualTo(ThemeSource.CUSTOM)
    }

    @Test
    fun `setSeed updates seed color`() {
        val argb = 0xFF1B6EF3.toInt()
        themeState.setSeed(argb)
        assertThat(themeState.config.value.seedArgb).isEqualTo(argb)
        assertThat(settings.get(SettingsKeys.THEME_CUSTOM_SEED)).isEqualTo(argb)
    }

    @Test
    fun `setStyle updates palette style`() {
        themeState.setStyle(PaletteStyle.Vibrant)
        assertThat(themeState.config.value.style).isEqualTo(PaletteStyle.Vibrant)
        assertThat(settings.get(SettingsKeys.THEME_PALETTE_STYLE)).isEqualTo(PaletteStyle.Vibrant.name)
    }

    @Test
    fun `changes survive re-creation like app restart`() {
        themeState.setMode(ThemeMode.LIGHT)
        themeState.setSource(ThemeSource.CUSTOM)
        themeState.setSeed(0xFF208040.toInt())
        themeState.setStyle(PaletteStyle.Expressive)

        // DataStore 串行化：no-op edit 排在所有 pending 写之后，其完成即代表落盘完成
        // （data.first() 只返回当前持久化状态，不等 pending 写，不能用于等待）
        runBlocking { context.settingsDataStore.edit { } }

        // 重新构造（新 SettingsRepository 构造时同步读取 DataStore 最新快照）
        val settings2 = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))
        val themeState2 = ThemeState(settings2)

        assertThat(themeState2.config.value.mode).isEqualTo(ThemeMode.LIGHT)
        assertThat(themeState2.config.value.source).isEqualTo(ThemeSource.CUSTOM)
        assertThat(themeState2.config.value.seedArgb).isEqualTo(0xFF208040.toInt())
        assertThat(themeState2.config.value.style).isEqualTo(PaletteStyle.Expressive)
    }
}

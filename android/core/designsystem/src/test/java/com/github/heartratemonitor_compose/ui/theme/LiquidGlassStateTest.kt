package com.github.heartratemonitor_compose.ui.theme

import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.AppSettings
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [LiquidGlassState] 液态玻璃配置状态测试。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LiquidGlassStateTest {

    private lateinit var context: android.app.Application
    private lateinit var settings: SettingsRepository
    private lateinit var state: LiquidGlassState

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SETTINGS_FILE_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking { context.settingsDataStore.edit { it.clear() } }
        settings = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))
        state = LiquidGlassState(settings)
    }

    @Test
    fun `initial config reflects persisted defaults`() {
        val config = state.config.value
        assertThat(config.enabled).isEqualTo(settings.get(SettingsKeys.LIQUID_GLASS_ENABLED))
        assertThat(config.blurDp).isEqualTo(AppSettings.DEFAULT_LIQUID_GLASS_BLUR_DP)
        assertThat(config.distortionDp).isEqualTo(AppSettings.DEFAULT_LIQUID_GLASS_DISTORTION_DP)
    }

    @Test
    fun `setEnabled updates state flow and persists`() {
        state.setEnabled(true)
        assertThat(state.config.value.enabled).isTrue()
        assertThat(settings.get(SettingsKeys.LIQUID_GLASS_ENABLED)).isTrue()

        state.setEnabled(false)
        assertThat(state.config.value.enabled).isFalse()
        assertThat(settings.get(SettingsKeys.LIQUID_GLASS_ENABLED)).isFalse()
    }

    @Test
    fun `setBlur and setDistortion update state flow and persist`() {
        state.setBlurDp(12f)
        assertThat(state.config.value.blurDp).isEqualTo(12f)
        assertThat(settings.get(SettingsKeys.LIQUID_GLASS_BLUR)).isEqualTo(12f)

        state.setDistortionDp(8f)
        assertThat(state.config.value.distortionDp).isEqualTo(8f)
        assertThat(settings.get(SettingsKeys.LIQUID_GLASS_DISTORTION)).isEqualTo(8f)
    }

    @Test
    fun `restoreDefaults resets to factory defaults`() {
        state.setBlurDp(40f)
        state.setDistortionDp(0f)

        state.restoreDefaults()

        assertThat(state.config.value.blurDp).isEqualTo(AppSettings.DEFAULT_LIQUID_GLASS_BLUR_DP)
        assertThat(state.config.value.distortionDp).isEqualTo(AppSettings.DEFAULT_LIQUID_GLASS_DISTORTION_DP)
        assertThat(settings.get(SettingsKeys.LIQUID_GLASS_BLUR))
            .isEqualTo(AppSettings.DEFAULT_LIQUID_GLASS_BLUR_DP)
        assertThat(settings.get(SettingsKeys.LIQUID_GLASS_DISTORTION))
            .isEqualTo(AppSettings.DEFAULT_LIQUID_GLASS_DISTORTION_DP)
    }

    @Test
    fun `changes survive re-creation like app restart`() {
        state.setEnabled(true)
        state.setBlurDp(18f)

        // DataStore 串行化：no-op edit 排在所有 pending 写之后，其完成即代表落盘完成
        runBlocking { context.settingsDataStore.edit { } }

        val settings2 = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))
        val state2 = LiquidGlassState(settings2)

        assertThat(state2.config.value.enabled).isTrue()
        assertThat(state2.config.value.blurDp).isEqualTo(18f)
    }
}

package com.github.heartratemonitor_compose.ui.theme

import android.content.Context
import android.content.SharedPreferences
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主题色彩来源。
 *
 * - [SYSTEM_MONET]：跟随系统壁纸 Monet 动态取色（Android 12+），低版本回退到 Expressive 静态方案。
 * - [CUSTOM]：用户自选 seed 色，由 MaterialKolor 生成 ColorScheme，**切断壁纸联动**。
 */
object ThemeSource {
    const val SYSTEM_MONET = 0
    const val CUSTOM = 1
}

/**
 * 主题明暗模式。与色彩来源正交，可任意组合。
 *
 * - [FOLLOW_SYSTEM]：跟随系统暗色模式。
 * - [LIGHT]：强制亮色。
 * - [DARK]：强制暗色。
 */
object ThemeMode {
    const val FOLLOW_SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
}

/**
 * 主题配置快照。由 [ThemeState] 持有并通过 [StateFlow] 暴露给 Compose 层。
 *
 * @param source 色彩来源，见 [ThemeSource]
 * @param mode 明暗模式，见 [ThemeMode]
 * @param seedArgb 自定义种子色（ARGB Int），仅 [ThemeSource.CUSTOM] 生效
 * @param style MaterialKolor variant，仅 [ThemeSource.CUSTOM] 生效
 */
data class ThemeConfig(
    val source: Int,
    val mode: Int,
    val seedArgb: Int,
    val style: PaletteStyle
)

/**
 * 全局主题状态单例。
 *
 * - 在 [HeartRateApp.onCreate] 中调用 [init] 注入 AppContext，从 `app_settings` 读取持久化配置。
 * - 设置页通过 [setSource]/[setMode]/[setSeed]/[setStyle] 修改配置，立即写回 SharedPreferences
 *   并更新 [config] StateFlow，Compose 层 collectAsState 后全 App 即时重配色，无需重启 Activity。
 *
 * 单进程内 Activity 与 Services（FloatingWindowService / StatusBarResidentService）共享同一实例，
 * 因此修改主题后悬浮窗与状态栏 overlay 也会同步换色。
 */
object ThemeState {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_SOURCE = "theme_source"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_SEED = "theme_custom_seed"
    private const val KEY_STYLE = "theme_palette_style"

    /** 默认种子色：Material 3 基线紫 (Baseline Purple)，与 M3 规范默认 seed 一致。 */
    private const val DEFAULT_SEED_ARGB = 0xFF6750A4.toInt()

    private val _config = MutableStateFlow(
        ThemeConfig(
            source = ThemeSource.SYSTEM_MONET,
            mode = ThemeMode.FOLLOW_SYSTEM,
            seedArgb = DEFAULT_SEED_ARGB,
            style = PaletteStyle.TonalSpot
        )
    )
    val config: StateFlow<ThemeConfig> = _config.asStateFlow()

    private lateinit var prefs: SharedPreferences

    /**
     * 在 Application.onCreate 中调用，注入 AppContext 并加载持久化配置。
     * 必须在任何 Composable 读取 [config] 之前完成（同一进程内仅调用一次）。
     */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val styleName = prefs.getString(KEY_STYLE, PaletteStyle.TonalSpot.name)
            ?: PaletteStyle.TonalSpot.name
        val style = runCatching { PaletteStyle.valueOf(styleName) }
            .getOrDefault(PaletteStyle.TonalSpot)
        _config.value = ThemeConfig(
            source = prefs.getInt(KEY_SOURCE, ThemeSource.SYSTEM_MONET),
            mode = prefs.getInt(KEY_MODE, ThemeMode.FOLLOW_SYSTEM),
            seedArgb = prefs.getInt(KEY_SEED, DEFAULT_SEED_ARGB),
            style = style
        )
    }

    fun setSource(source: Int) {
        prefs.edit().putInt(KEY_SOURCE, source).apply()
        _config.value = _config.value.copy(source = source)
    }

    fun setMode(mode: Int) {
        prefs.edit().putInt(KEY_MODE, mode).apply()
        _config.value = _config.value.copy(mode = mode)
    }

    fun setSeed(argb: Int) {
        prefs.edit().putInt(KEY_SEED, argb).apply()
        _config.value = _config.value.copy(seedArgb = argb)
    }

    fun setStyle(style: PaletteStyle) {
        prefs.edit().putString(KEY_STYLE, style.name).apply()
        _config.value = _config.value.copy(style = style)
    }
}

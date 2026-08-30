package com.github.heartratemonitor_compose.ui.settings

import android.content.Intent
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.system.OverlayPermissionProvider
import com.github.heartratemonitor_compose.service.ServiceLauncher
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 状态栏常驻设置页面的 ViewModel（MVI 架构，Phase 1）。
 *
 * 职责：
 * - 从 [SettingsRepository.settings] 全量快照派生状态栏设置：
 *   UiState 是设置真源的派生投影，Flow 回流经 [setState] 归约（状态下行）。
 * - 开关/滑块/颜色事件经 [StatusBarSettingsIntent] dispatch 上行：
 *   滑块 onValueChange 每拍写入（set 为乐观 CAS + 异步落盘，与原直写同路径，
 *   不加节流以免破坏写后立读语义）；颜色确认经 ConfirmColor 回写。
 * - 常驻开关的权限判定与服务启停联动归 SetResident handler（原 UI 层
 *   EntryPoint 直调上提）；无权限时经 Intent 内的 [StatusBarSettingsIntent.SetResident.onRequestPermission]
 *   回调交由 UI 跳转（Activity 上下文，§3.4 方案 1）。
 */
@HiltViewModel
class StatusBarSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val overlayPermissionProvider: OverlayPermissionProvider,
    private val serviceLauncher: ServiceLauncher,
    private val suppressHideForExternalLaunch: @JvmSuppressWildcards (Boolean) -> Unit
) : MviViewModel<StatusBarSettingsUiState, StatusBarSettingsIntent>(initialStatusBarSettingsUiState(settings)) {

    init {
        // 设置真源投影：每次快照变化原子归约进 UiState，禁止本地双写（§3.5）
        viewModelScope.launch {
            settings.settings.collect { s ->
                setState {
                    it.copy(
                        residentEnabled = s.statusBarResidentEnabled,
                        bpmTextEnabled = s.statusBarBpmTextEnabled,
                        xPosition = s.statusBarXPosition,
                        yOffset = s.statusBarYOffset,
                        size = s.statusBarSize,
                        textThickness = s.statusBarTextThickness,
                        textColor = s.statusBarTextColor
                    )
                }
            }
        }
    }

    override suspend fun handleIntent(intent: StatusBarSettingsIntent) {
        when (intent) {
            is StatusBarSettingsIntent.SetResident -> {
                if (intent.enabled && !overlayPermissionProvider.canDrawOverlays()) {
                    // 无悬浮窗权限：不落盘不启服务，经回调交由 UI 跳转系统权限页
                    //（Activity 上下文）；DEF-02 修复：先回调跳转，UI 侧 startActivity 成功后才
                    // 置位 suppress——避免 safe-cast 失败或 Intent 不解析时 suppress 泄漏。
                    // setSuppressHideForExternalLaunch 自带 5 秒超时复位兜底。
                    intent.onRequestPermission?.invoke(overlayPermissionProvider.createManageOverlayIntent())
                    suppressHideForExternalLaunch(true)
                } else {
                    settings.set(SettingsKeys.STATUS_BAR_RESIDENT_ENABLED, intent.enabled)
                    if (intent.enabled) serviceLauncher.startStatusBarResidentService()
                    else serviceLauncher.stopStatusBarResidentService()
                }
            }
            is StatusBarSettingsIntent.SetBpmText ->
                settings.set(SettingsKeys.STATUS_BAR_BPM_TEXT_ENABLED, intent.enabled)
            is StatusBarSettingsIntent.SetXPosition ->
                settings.set(SettingsKeys.STATUS_BAR_X_POSITION, intent.value)
            is StatusBarSettingsIntent.SetYOffset ->
                settings.set(SettingsKeys.STATUS_BAR_Y_OFFSET, intent.value)
            is StatusBarSettingsIntent.SetSize ->
                settings.set(SettingsKeys.STATUS_BAR_SIZE, intent.value)
            is StatusBarSettingsIntent.SetTextThickness ->
                settings.set(SettingsKeys.STATUS_BAR_TEXT_THICKNESS, intent.value)
            is StatusBarSettingsIntent.SetTextColor ->
                settings.set(SettingsKeys.STATUS_BAR_TEXT_COLOR, intent.color)
            is StatusBarSettingsIntent.ConfirmColor ->
                settings.set(intent.key, intent.color)
        }
    }
}

/** 状态栏设置页用户意图。 */
sealed interface StatusBarSettingsIntent {
    /**
     * 常驻开关。无悬浮窗权限时经 [onRequestPermission] 回传权限页 Intent，
     * 由 UI（Activity 上下文）执行跳转（§3.4 方案 1 回调形态）。
     */
    data class SetResident(
        val enabled: Boolean,
        val onRequestPermission: ((Intent) -> Unit)? = null
    ) : StatusBarSettingsIntent
    data class SetBpmText(val enabled: Boolean) : StatusBarSettingsIntent
    data class SetXPosition(val value: Int) : StatusBarSettingsIntent
    data class SetYOffset(val value: Int) : StatusBarSettingsIntent
    data class SetSize(val value: Int) : StatusBarSettingsIntent
    data class SetTextThickness(val value: Int) : StatusBarSettingsIntent
    data class SetTextColor(val color: Int) : StatusBarSettingsIntent

    /** 颜色选择器确认回写（选择哪个键由 UI 的瞬时态 ColorPickerRequest 决定）。 */
    data class ConfirmColor(val key: Preferences.Key<Int>, val color: Int) : StatusBarSettingsIntent
}

/** 状态栏设置页 UI 状态（只读快照）。 */
data class StatusBarSettingsUiState(
    val residentEnabled: Boolean,
    val bpmTextEnabled: Boolean,
    val xPosition: Int,
    val yOffset: Int,
    val size: Int,
    val textThickness: Int,
    val textColor: Int
)

/**
 * 初始状态：读 [SettingsRepository] 内存快照真实值（app 启动时已预热、零 IO），
 * 消除进入页面时"先默认值后快照覆盖"的闪变；键缺失时 [SettingsRepository.get]
 * 回落 [AppSettings.DEFAULTS]（契约 10.3）。
 */
internal fun initialStatusBarSettingsUiState(settings: SettingsRepository): StatusBarSettingsUiState = StatusBarSettingsUiState(
    residentEnabled = settings.get(SettingsKeys.STATUS_BAR_RESIDENT_ENABLED),
    bpmTextEnabled = settings.get(SettingsKeys.STATUS_BAR_BPM_TEXT_ENABLED),
    xPosition = settings.get(SettingsKeys.STATUS_BAR_X_POSITION),
    yOffset = settings.get(SettingsKeys.STATUS_BAR_Y_OFFSET),
    size = settings.get(SettingsKeys.STATUS_BAR_SIZE),
    textThickness = settings.get(SettingsKeys.STATUS_BAR_TEXT_THICKNESS),
    textColor = settings.get(SettingsKeys.STATUS_BAR_TEXT_COLOR)
)

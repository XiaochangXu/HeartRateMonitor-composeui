package com.github.heartratemonitor_compose.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.heartratemonitor_compose.service.KillStateSaver
import com.github.heartratemonitor_compose.ui.animation.LocalReducedMotion
import com.github.heartratemonitor_compose.ui.animation.rememberSystemReducedMotion
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import com.github.heartratemonitor_compose.ui.settings.ChangelogBottomSheet
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassState
import kotlinx.coroutines.delay

// 首帧绘制后再等窗口/启动转场稳定，保证更新日志 BottomSheet 的展开动画有帧可跑
private const val CHANGELOG_UI_SETTLE_MS = 300L
// 权限流程超时兜底（自 UI 就绪起算），防止回调丢失导致更新日志永不展示
private const val CHANGELOG_PERMISSION_TIMEOUT_MS = 3000L

@Composable
fun AppRoot(
    changelogNotifier: ChangelogNotifier,
    liquidGlassState: LiquidGlassState,
    killStateSaver: KillStateSaver,
    onToggleFloatingWindow: () -> Unit,
    onOpenExternal: (Intent) -> Unit
) {
    val mainViewModel: MainViewModel = hiltViewModel()
    var currentTab by remember { mutableStateOf<Screen>(Screen.Home) }

    AppLifecycleEffects(
        mainViewModel = mainViewModel,
        killStateSaver = killStateSaver,
        currentTab = currentTab
    )

    val changelogNotice by changelogNotifier.notice.collectAsStateWithLifecycle()

    // 更新日志放行编排：首帧真实绘制 + 窗口稳定后放行；权限回调迟迟不来则超时强制放行。
    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(CHANGELOG_UI_SETTLE_MS)
        changelogNotifier.markUiReady()
        delay(CHANGELOG_PERMISSION_TIMEOUT_MS)
        changelogNotifier.markPermissionsSettled()
    }

    val reducedMotion = rememberSystemReducedMotion()

    val liquidGlassConfig by liquidGlassState.config.collectAsStateWithLifecycle()

    val navAnimationDisabled by liquidGlassState.navAnimationDisabledFlow.collectAsStateWithLifecycle()

    // 底部渐变需覆盖：系统导航条 inset + 悬浮应用导航条（FLOATING_NAV_HEIGHT + 边距）。
    // windowInsetsBottomHeight 仅等于系统导航条高度，无法覆盖应用导航条悬浮区域。
    val density = LocalDensity.current
    val navBarInsetPx = WindowInsets.navigationBars.getBottom(density)
    val bottomGradientHeightDp = with(density) {
        (navBarInsetPx + FLOATING_NAV_HEIGHT + FLOATING_NAV_BOTTOM_MARGIN).toDp()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
            AppTabHost(
                viewModel = mainViewModel,
                onToggleFloatingWindow = onToggleFloatingWindow,
                onOpenExternal = onOpenExternal,
                liquidGlassConfig = liquidGlassConfig,
                onCurrentTabChange = { currentTab = it },
                killStateSaver = killStateSaver,
                navAnimationDisabled = navAnimationDisabled
            )

            // 顶部渐变：状态栏区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                            1f to Color.Transparent
                        )
                    )
            )
            // 底部渐变：覆盖系统导航条 inset + 悬浮应用导航条区域。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomGradientHeightDp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
                        )
                    )
            )

            val notice = changelogNotice

            if (notice != null) {
                ChangelogBottomSheet(
                    changelogContent = notice.content,
                    currentVersion = notice.versionName,
                    onDismiss = { changelogNotifier.dismiss() }
                )
            }
        }
    }
}

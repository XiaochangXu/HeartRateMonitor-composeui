package com.github.heartratemonitor_compose.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope.PlaceholderSize.Companion.AnimatedSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.heartratemonitor_compose.feature.settings.R
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassState
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim
import com.github.heartratemonitor_compose.ui.util.bottomCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavStyleScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: NavStyleViewModel = hiltViewModel()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                title = {
                    Text(
                        stringResource(R.string.nav_style),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceBright
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cd_back)
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(padding.calculateTopPadding() + 16.dp))

            // 液态玻璃需 Android 12 (API 31+)，更低版本展示不支持提示
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Text(
                    text = stringResource(R.string.liquid_glass_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                NavigationEffectsSection(viewModel = viewModel)
            }

              Spacer(Modifier.height(64.dp + 8.dp))
            }
            StatusBarScrim()
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NavigationEffectsSection(viewModel: NavStyleViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val config = uiState.config
    val enabled = config.enabled
    // lens（扭曲）效果需 RuntimeShader，仅 Android 13 (API 33+) 支持
    val supportsDistortion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    // 开关行底部圆角动画：enabled 时底部直角（下方有滑块），disabled 时底部圆角（自己是最后一项）
    val switchBottomCorner by animateDpAsState(
        targetValue = if (enabled) 0.dp else 28.dp,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "switchBottomCorner"
    )
    val switchShape = bottomCornerShape(switchBottomCorner)

    // SharedTransitionLayout 创建 LookaheadScope，使子布局的增删与尺寸变化能协调插值
    SharedTransitionLayout {
        SettingsGroupCard {
            // Header: icon + 标题 + 恢复默认按钮
            SettingsItem(isFirst = true) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = containerColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_nav_effects),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = iconTint
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.nav_settings_subtitle),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        ExpressiveButton(
                            label = stringResource(R.string.restore_default),
                            onClick = { viewModel.dispatch(NavStyleIntent.RestoreDefaults) }
                        )
                    }
                    Text(
                        text = stringResource(R.string.liquid_glass_support_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 56.dp)
                    )
                }
            }

            // 开关行：使用动画驱动 shape 替代静态 isLast，圆角连续变形
            SettingsItem(shape = switchShape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.liquid_glass_enable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { viewModel.dispatch(NavStyleIntent.SetEnabled(it)) }
                    )
                }
            }

            // 滑块区域：AnimatedVisibility 提供进/出动画与 AnimatedVisibilityScope
            // sharedBounds 配合 AnimatedSize placeholder 使父布局感知到动画中的尺寸变化
            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically(
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = tween(250)),
                exit = shrinkVertically(
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = tween(250))
            ) {
                val sliderBounds = rememberSharedContentState(key = "liquid_glass_sliders")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sharedBounds(
                            sharedContentState = sliderBounds,
                            animatedVisibilityScope = this,
                            boundsTransform = { _, _ ->
                                tween(250, easing = FastOutSlowInEasing)
                            },
                            placeholderSize = AnimatedSize,
                            enter = fadeIn(tween(250)),
                            exit = fadeOut(tween(250))
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 模糊滑块
                    SettingsItem(isLast = !supportsDistortion) {
                        NavSliderRow(
                            title = stringResource(R.string.liquid_glass_blur),
                            value = config.blurDp,
                            valueRange = LiquidGlassState.BLUR_RANGE_DP,
                            onValueChange = { viewModel.dispatch(NavStyleIntent.SetBlurDp(it)) }
                        )
                    }

                    // 扭曲滑块
                    if (supportsDistortion) {
                        SettingsItem(isLast = true) {
                            NavSliderRow(
                                title = stringResource(R.string.liquid_glass_distortion),
                                value = config.distortionDp,
                                valueRange = LiquidGlassState.DISTORTION_RANGE_DP,
                                onValueChange = { viewModel.dispatch(NavStyleIntent.SetDistortionDp(it)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "%.0f".format(value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = 0,
            // 与共享 DragSlider（SettingsComponents.kt）配色保持一致，
            // 否则原生 Slider 默认色（inactiveTrack 走 surfaceContainerHighest 色阶）与
            // 悬浮窗/状态栏设置页的滑块轨道颜色不一致
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledThumbColor = MaterialTheme.colorScheme.outline,
                disabledActiveTrackColor = MaterialTheme.colorScheme.outline,
                disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

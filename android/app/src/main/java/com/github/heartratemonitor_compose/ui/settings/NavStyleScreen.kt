package com.github.heartratemonitor_compose.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassState

/**
 * 导航样式二级页面。
 *
 * 承载原位于 SettingsScreen 的"导航栏效果"卡片模块（液态玻璃开关与滑块）。
 * 设计风格与 ThemeSettingsScreen / HeartRateAlarmScreen 保持一致：
 * - TopAppBar 标题 + 圆形返回按钮
 * - 单组 SettingsGroupCard，圆角 28dp
 * - 末尾留出胶囊+系统导航栏空间
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavStyleScreen(
    onNavigateBack: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
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
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_back)
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // 液态玻璃需 Android 12 (API 31+)，更低版本展示不支持提示
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Text(
                    text = stringResource(R.string.liquid_glass_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                NavigationEffectsSection()
            }

            // 末尾留出胶囊+系统导航栏空间
            Spacer(Modifier.height(64.dp + 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}

/**
 * 导航栏效果卡片：液态玻璃开关、模糊滑块、扭曲滑块。
 *
 * 从 SettingsScreen 移入，内部逻辑保持不变。卡片 Header icon 改用
 * 导航栏效果.svg (ic_nav_effects) 以匹配"导航样式"页面的语义层级。
 */
@Composable
private fun NavigationEffectsSection() {
    val config by LiquidGlassState.config.collectAsState()
    val enabled = config.enabled
    // lens（扭曲）效果需 RuntimeShader，仅 Android 13 (API 33+) 支持
    val supportsDistortion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

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
                    TextButton(onClick = { LiquidGlassState.restoreDefaults() }) {
                        Text(stringResource(R.string.restore_default))
                    }
                }
                Text(
                    text = stringResource(R.string.liquid_glass_support_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 56.dp)
                )
            }
        }

        // 液态玻璃开关
        SettingsItem(isLast = !enabled) {
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
                    onCheckedChange = { LiquidGlassState.setEnabled(it) }
                )
            }
        }

        // 模糊滑块
        if (enabled) {
            SettingsItem(isLast = !supportsDistortion) {
                NavSliderRow(
                    title = stringResource(R.string.liquid_glass_blur),
                    value = config.blurDp,
                    valueRange = LiquidGlassState.BLUR_RANGE_DP,
                    onValueChange = { LiquidGlassState.setBlurDp(it) }
                )
            }

            // 扭曲滑块
            if (supportsDistortion) {
                SettingsItem(isLast = true) {
                    NavSliderRow(
                        title = stringResource(R.string.liquid_glass_distortion),
                        value = config.distortionDp,
                        valueRange = LiquidGlassState.DISTORTION_RANGE_DP,
                        onValueChange = { LiquidGlassState.setDistortionDp(it) }
                    )
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
            steps = 0
        )
    }
}

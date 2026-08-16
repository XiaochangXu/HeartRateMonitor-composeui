package com.github.heartratemonitor_compose.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.feature.settings.R
import com.github.heartratemonitor_compose.ui.util.segmentedItemShape
import com.github.heartratemonitor_compose.ui.widgets.CapsuleSegmentedButton
import com.github.heartratemonitor_compose.ui.widgets.IconContainer
import com.github.heartratemonitor_compose.ui.widgets.SegmentOption
import com.materialkolor.PaletteStyle

// ──────────────────────────────────────────────
// 主题设置页通用容器（本页私有副本，与 settings 包同名组件独立演进）
// ──────────────────────────────────────────────

@Composable
internal fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

@Composable
internal fun SettingsItem(
    enabled: Boolean = true,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = segmentedItemShape(isFirst, isLast)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!enabled) Modifier.alpha(0.45f) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}

/** 主题色分组标题行 */
@Composable
internal fun HeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconContainer(icon = painterResource(R.drawable.ic_color_palette))
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.theme_color),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 自定义主题开关行 */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 主题模式选择行：跟随系统/浅色/深色 */
@Composable
internal fun ThemeModeRow(
    currentMode: Int,
    onModeSelected: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.theme_mode),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        CapsuleSegmentedButton(
            options = listOf(
                SegmentOption(
                    label = stringResource(R.string.theme_mode_system),
                    icon = Icons.Outlined.BrightnessAuto,
                    value = ThemeMode.FOLLOW_SYSTEM
                ),
                SegmentOption(
                    label = stringResource(R.string.theme_mode_light),
                    icon = Icons.Outlined.LightMode,
                    value = ThemeMode.LIGHT
                ),
                SegmentOption(
                    label = stringResource(R.string.theme_mode_dark),
                    icon = Icons.Outlined.DarkMode,
                    value = ThemeMode.DARK
                )
            ),
            selectedValue = currentMode,
            onOptionSelected = onModeSelected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ──────────────────────────────────────────────
// 自定义主题色入口
// ──────────────────────────────────────────────

/** 自定义种子色入口行：点击打开取色器 */
@Composable
internal fun CustomSeedRow(
    seedArgb: Int,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.theme_custom_seed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(16.dp))
        val seedShape = MaterialTheme.shapes.small
        Surface(
            modifier = Modifier
                .size(40.dp)
                 
                .clip(seedShape)
                .clickable(enabled = enabled, onClick = onClick),
            shape = seedShape,
            color = Color(seedArgb),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline
            )
        ) {}
    }
}

/** PaletteStyle variant 选择行（横向滚动 FilterChip） */
@Composable
internal fun VariantSelectorRow(
    currentStyle: PaletteStyle,
    onStyleSelected: (PaletteStyle) -> Unit,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.theme_variant),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PALETTE_STYLES.forEach { (style, labelRes) ->
                FilterChip(
                    selected = currentStyle == style,
                    onClick = { onStyleSelected(style) },
                    enabled = enabled,
                    label = { Text(stringResource(labelRes)) }
                )
            }
        }
    }
}

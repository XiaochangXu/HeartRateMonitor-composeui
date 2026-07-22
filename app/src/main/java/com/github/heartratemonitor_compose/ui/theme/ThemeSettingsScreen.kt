package com.github.heartratemonitor_compose.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ui.settings.ColorPickerDialog
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 主题设置二级页面。
 *
 * 卡片内包含 5 个设置项：
 * 1. 自定义主题开关（OFF=系统 Monet 默认 / ON=自选 seed）
 * 2. 主题模式（跟随系统 / 亮色 / 暗色），与色彩来源正交，始终可用
 * 3. 预设色卡（仅自定义模式可用）
 * 4. 自定义主题色（HSV 色轮，仅自定义模式可用）
 * 5. PaletteStyle variant（9 种，仅自定义模式可用）
 *
 * 修改通过 [ThemeState] 立即写回 SharedPreferences 并触发全 App 重组。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val config by ThemeState.config.collectAsState()
    val isCustom = config.source == ThemeSource.CUSTOM
    var showSeedPicker by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.theme_settings),
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
                }
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
            SectionTitle(stringResource(R.string.theme_color))
            SettingsGroupCard {
                SettingsItem(isFirst = true) {
                    HeaderRow()
                }

                // 1. 自定义主题开关
                SettingsItem {
                    SettingsSwitchRow(
                        title = stringResource(R.string.theme_custom),
                        checked = isCustom,
                        onCheckedChange = { checked ->
                            ThemeState.setSource(
                                if (checked) ThemeSource.CUSTOM else ThemeSource.SYSTEM_MONET
                            )
                        }
                    )
                }

                // 2. 主题模式（始终可用）
                SettingsItem {
                    ThemeModeRow(
                        currentMode = config.mode,
                        onModeSelected = { ThemeState.setMode(it) }
                    )
                }

                // 3. 预设色卡（仅自定义模式可用）
                SettingsItem(enabled = isCustom) {
                    PresetSeedsRow(
                        currentSeed = config.seedArgb,
                        style = config.style,
                        onSeedSelected = { ThemeState.setSeed(it) },
                        enabled = isCustom
                    )
                }

                // 4. 自定义种子色（仅自定义模式可用）
                SettingsItem(enabled = isCustom) {
                    CustomSeedRow(
                        seedArgb = config.seedArgb,
                        onClick = { showSeedPicker = true },
                        enabled = isCustom
                    )
                }

                // 5. PaletteStyle variant（仅自定义模式可用）
                SettingsItem(enabled = isCustom, isLast = true) {
                    VariantSelectorRow(
                        currentStyle = config.style,
                        onStyleSelected = { ThemeState.setStyle(it) },
                        enabled = isCustom
                    )
                }
            }
            // 末尾留出胶囊+系统导航栏空间
            Spacer(Modifier.height(64.dp + 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }

    if (showSeedPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.theme_custom_seed),
            initialColor = config.seedArgb,
            onConfirm = {
                ThemeState.setSeed(it)
                showSeedPicker = false
            },
            onDismiss = { showSeedPicker = false }
        )
    }
}

// ──────────────────────────────────────────────
// 容器与通用组件（本文件自包含，与 SettingsScreen 风格一致）
// ──────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

@Composable
private fun SettingsItem(
    enabled: Boolean = true,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = when {
        isFirst && isLast -> RoundedCornerShape(28.dp)
        isFirst -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        isLast -> RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
        else -> RoundedCornerShape(0.dp)
    }
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

@Composable
private fun HeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer, 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_color_palette),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.theme_color),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsSwitchRow(
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

/**
 * 主题模式选择：跟随系统 / 亮色 / 暗色。
 * 使用 M3 SingleChoiceSegmentedButtonRow。
 */
@Composable
private fun ThemeModeRow(
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
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                Triple(ThemeMode.FOLLOW_SYSTEM, R.string.theme_mode_system, 0),
                Triple(ThemeMode.LIGHT, R.string.theme_mode_light, 1),
                Triple(ThemeMode.DARK, R.string.theme_mode_dark, 2)
            )
            options.forEach { (mode, labelRes, index) ->
                SegmentedButton(
                    selected = currentMode == mode,
                    onClick = { onModeSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(labelRes),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// 预设色卡预览缓存
// ──────────────────────────────────────────────

/** 缓存 PresetSeedCard 的预览 ColorScheme，避免每次进入页面重复计算。 */
internal object ThemePreviewCache {
    private val cache = ConcurrentHashMap<Pair<Int, PaletteStyle>, ColorScheme>()

    suspend fun get(seedArgb: Int, style: PaletteStyle): ColorScheme {
        val key = seedArgb to style
        val cached = cache[key]
        if (cached != null) return cached
        return withContext(Dispatchers.Default) {
            cache.getOrPut(key) {
                dynamicColorScheme(
                    seedColor = Color(seedArgb),
                    isDark = false,
                    style = style
                )
            }
        }
    }

    /** 在后台预计算所有预设种子 × 所有 PaletteStyle 的预览方案。 */
    fun preload(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            PRESET_SEEDS.forEach { preset ->
                PALETTE_STYLES.forEach { (style, _) ->
                    get(preset.argb, style)
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// 预设色卡
// ──────────────────────────────────────────────

private data class PresetSeed(val nameRes: Int, val argb: Int)

/** 8 个覆盖色相轮的预设种子色。 */
private val PRESET_SEEDS = listOf(
    PresetSeed(R.string.theme_seed_red, 0xFFD02020.toInt()),
    PresetSeed(R.string.theme_seed_orange, 0xFFE07A00.toInt()),
    PresetSeed(R.string.theme_seed_yellow, 0xFFB08000.toInt()),
    PresetSeed(R.string.theme_seed_green, 0xFF208040.toInt()),
    PresetSeed(R.string.theme_seed_teal, 0xFF008080.toInt()),
    PresetSeed(R.string.theme_seed_blue, 0xFF1B6EF3.toInt()),
    PresetSeed(R.string.theme_seed_purple, 0xFF6750A4.toInt()),
    PresetSeed(R.string.theme_seed_pink, 0xFFB04080.toInt())
)

/**
 * 预设色卡网格：4 列 × 2 行，共 8 个圆角正方形卡片。
 *
 * 预览始终用亮色方案（isDark=false），作为色彩组合的规范化展示，
 * 与 Android 系统壁纸取色预览一致。
 */
@Composable
private fun PresetSeedsRow(
    currentSeed: Int,
    style: PaletteStyle,
    onSeedSelected: (Int) -> Unit,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.theme_preset_seeds),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        // 8 个预设分 2 排，每排 4 个
        val rows = PRESET_SEEDS.chunked(4)
        rows.forEachIndexed { rowIndex, rowItems ->
            if (rowIndex > 0) Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { preset ->
                    key(preset.argb, style) {
                        PresetSeedCard(
                            preset = preset,
                            selected = currentSeed == preset.argb,
                            style = style,
                            onClick = { onSeedSelected(preset.argb) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetSeedCard(
    preset: PresetSeed,
    selected: Boolean,
    style: PaletteStyle,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 用 MaterialKolor 从 seed 生成亮色 ColorScheme 作为预览
    val previewScheme by produceState(
        initialValue = MaterialTheme.colorScheme,
        key1 = preset.argb,
        key2 = style
    ) {
        value = ThemePreviewCache.get(preset.argb, style)
    }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = previewScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            // 中心 primary 色块作为主预览
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.6f)
                    .height(10.dp)
                    .background(previewScheme.primary, RoundedCornerShape(5.dp))
            )
            if (selected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .background(previewScheme.secondary, RoundedCornerShape(3.dp))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .background(previewScheme.tertiary, RoundedCornerShape(3.dp))
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(preset.nameRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = previewScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ──────────────────────────────────────────────
// 自定义主题色入口
// ──────────────────────────────────────────────

@Composable
private fun CustomSeedRow(
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
        Surface(
            modifier = Modifier
                .size(40.dp)
                .clickable(enabled = enabled, onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color = Color(seedArgb),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline
            )
        ) {}
    }
}

// ──────────────────────────────────────────────
// PaletteStyle variant 选择器
// ──────────────────────────────────────────────

/** 9 种 PaletteStyle 及其本地化名称。 */
private val PALETTE_STYLES = listOf(
    PaletteStyle.TonalSpot to R.string.theme_variant_tonal_spot,
    PaletteStyle.Vibrant to R.string.theme_variant_vibrant,
    PaletteStyle.Expressive to R.string.theme_variant_expressive,
    PaletteStyle.Neutral to R.string.theme_variant_neutral,
    PaletteStyle.Monochrome to R.string.theme_variant_monochrome,
    PaletteStyle.Fidelity to R.string.theme_variant_fidelity,
    PaletteStyle.Content to R.string.theme_variant_content,
    PaletteStyle.Rainbow to R.string.theme_variant_rainbow,
    PaletteStyle.FruitSalad to R.string.theme_variant_fruit_salad
)

@Composable
private fun VariantSelectorRow(
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

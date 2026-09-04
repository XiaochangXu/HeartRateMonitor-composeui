package com.github.heartratemonitor_compose.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.feature.settings.R
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** 预设种子色动态配色方案缓存：避免色卡预览重复计算（Hilt 单例） */
@Singleton
class ThemePreviewCache @Inject constructor() {
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

internal data class PresetSeed(val nameRes: Int, val argb: Int)

internal val PRESET_SEEDS = listOf(
    PresetSeed(R.string.theme_seed_red, 0xFFD02020.toInt()),
    PresetSeed(R.string.theme_seed_orange, 0xFFE07A00.toInt()),
    PresetSeed(R.string.theme_seed_yellow, 0xFFB08000.toInt()),
    PresetSeed(R.string.theme_seed_green, 0xFF208040.toInt()),
    PresetSeed(R.string.theme_seed_teal, 0xFF008080.toInt()),
    PresetSeed(R.string.theme_seed_blue, 0xFF1B6EF3.toInt()),
    PresetSeed(R.string.theme_seed_purple, 0xFF6750A4.toInt()),
    PresetSeed(R.string.theme_seed_pink, 0xFFB04080.toInt())
)

internal val PALETTE_STYLES = listOf(
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

/** 预设色卡网格（仅自定义模式可用）；预览缓存由 ThemeSettingsViewModel 下发 */
@Composable
internal fun PresetSeedsRow(
    currentSeed: Int,
    style: PaletteStyle,
    onSeedSelected: (Int) -> Unit,
    previewCache: ThemePreviewCache,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.theme_preset_seeds),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
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
                            previewCache = previewCache,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** 单个预设色卡：动态配色预览 + 选中态 */
@Composable
private fun PresetSeedCard(
    preset: PresetSeed,
    selected: Boolean,
    style: PaletteStyle,
    onClick: () -> Unit,
    previewCache: ThemePreviewCache,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val previewScheme by produceState(
        initialValue = MaterialTheme.colorScheme,
        key1 = preset.argb,
        key2 = style
    ) {
        value = previewCache.get(preset.argb, style)
    }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val cardShape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier
            .aspectRatio(1f)
           
            .clip(cardShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = cardShape,
        color = previewScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
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
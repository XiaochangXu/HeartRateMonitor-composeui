package com.github.heartratemonitor_compose.ui.widgets

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.github.heartratemonitor_compose.ui.animation.DampedDragAnimation
import com.github.heartratemonitor_compose.ui.animation.InteractiveHighlight
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassConfig
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** 单 Tab 配置 */
data class GlassTabItem(
    val iconRes: Int,
    val label: String
)

/** 按压时 Tab 缩放比例的 CompositionLocal */
private val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

/**
 * 悬浮胶囊式底部导航栏（三层液态玻璃结构）。
 *
 * 衍生自 AndroidLiquidGlass catalog LiquidBottomTabs，适配本项目：
 * - 底层：完整背景，vibrancy + blur + lens + shadow 常驻，按下整体缩放
 * - 中层：透明录制层（layerBackdrop），按下时 lens / highlight 随 pressProgress 增强
 * - 顶层：滑动指示器，CombinedBackdrop 采样，速度感知形变 + innerShadow
 *
 * @param backdrop 已录制 Tab 内容层的 Backdrop（由 AppRoot 通过 layerBackdrop 挂载）
 * @param selectedTabIndex 当前选中 Tab 索引（读 pagerState）
 * @param onTabSelected 切换 Tab 回调
 * @param tabs Tab 配置（icon + label）
 * @param config 液态玻璃配置（blur / distortion 半径）
 */
@Composable
fun FloatingBottomBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<GlassTabItem>,
    config: LiquidGlassConfig,
    interactive: () -> Boolean = { true }
) {
    val tabsCount = tabs.size
    val isLightTheme = !isSystemInDarkTheme()
    val isBlurEnabled = config.enabled
    val supportsLens = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val accentColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
        alpha = if (isBlurEnabled) 0.4f else 1f
    )

    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        var currentIndex by remember(selectedTabIndex) { mutableIntStateOf(selectedTabIndex()) }

        
        val currentOnTabSelected by rememberUpdatedState(onTabSelected)

        val dampedDragAnimation = remember(animationScope, tabsCount, density) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    currentOnTabSelected(targetIndex)
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
                enabled = interactive
            )
        }

        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }.collectLatest { index ->
                currentIndex = index
            }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                }
        }

        val interactiveHighlight = remember(animationScope, tabWidth) {
            if (isBlurEnabled && supportsLens) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    enabled = interactive,
                    position = { size, _ ->
                        Offset(
                            if (isLtr) {
                                (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                            } else {
                                size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                            },
                            size.height / 2f
                        )
                    }
                )
            } else {
                null
            }
        }

        // ── 底层：完整背景（vibrancy + blur + lens 常驻，layerBlock 按下整体缩放）──
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        if (isBlurEnabled && interactive()) {
                            vibrancy()
                            blur(config.blurDp.dp.toPx())
                            if (supportsLens) {
                                lens(config.distortionDp.dp.toPx(), config.distortionDp.dp.toPx())
                            }
                        }
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = if (isBlurEnabled) 1f else 0f)
                    },
                    shadow = {
                        Shadow.Default.copy(
                            color = Color.Black.copy(if (isLightTheme) 0.1f else 0.2f)
                        )
                    },
                    innerShadow = {
                        InnerShadow(radius = 4.dp, alpha = 0.1f)
                    },
                    layerBlock = {
                        if (isBlurEnabled) {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        }
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight?.modifier ?: Modifier)
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = { TabsContent(tabs, selectedTabIndex, onTabSelected) }
        )

        // ── 中层：透明录制层（layerBackdrop 录制 tab 内容，pressProgress 驱动 lens/highlight）──
        CompositionLocalProvider(
            LocalFloatingBottomBarTabScale provides {
                if (isBlurEnabled) lerp(1f, 1.2f, dampedDragAnimation.pressProgress) else 1f
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousCapsule },
                        effects = {
                            if (isBlurEnabled && interactive()) {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(config.blurDp.dp.toPx())
                                if (supportsLens) {
                                    lens(
                                        config.distortionDp.dp.toPx() * progress,
                                        config.distortionDp.dp.toPx() * progress
                                    )
                                }
                            }
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = if (isBlurEnabled) progress else 0f)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight?.modifier ?: Modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = { TabsContent(tabs, selectedTabIndex, onTabSelected) }
            )
        }

        // ── 顶层：滑动指示器（CombinedBackdrop 采样两层，速度感知形变）──
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = if (isLtr) {
                        dampedDragAnimation.value * tabWidth + panelOffset
                    } else {
                        size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    }
                }
                .then(interactiveHighlight?.gestureModifier ?: Modifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { ContinuousCapsule },
                    effects = {
                        if (isBlurEnabled && supportsLens && interactive()) {
                            val progress = dampedDragAnimation.pressProgress
                            lens(
                                10f.dp.toPx() * progress,
                                14f.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        }
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = if (isBlurEnabled) progress else 0f)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = if (isBlurEnabled) progress else 0f)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8.dp * progress,
                            alpha = if (isBlurEnabled) progress else 0f
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}

/**
 * Tab 内容：Icon + 常驻 label，按下时整体缩放。
 * 由 [LocalFloatingBottomBarTabScale] 驱动缩放比例。
 */
@Composable
private fun RowScope.TabsContent(
    tabs: List<GlassTabItem>,
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit
) {
    val scale = LocalFloatingBottomBarTabScale.current
    val currentIndex = selectedTabIndex()
    tabs.forEachIndexed { index, tab ->
        val selected = index == currentIndex
        val iconColor by animateColorAsState(
            targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(200),
            label = "tabIconColor"
        )
        val textColor by animateColorAsState(
            targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(200),
            label = "tabTextColor"
        )
        Column(
            modifier = Modifier
                .clip(ContinuousCapsule)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Tab,
                    onClick = { onTabSelected(index) }
                )
                .fillMaxHeight()
                .weight(1f)
                .graphicsLayer {
                    val currentScale = scale()
                    scaleX = currentScale
                    scaleY = currentScale
                },
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(tab.iconRes),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

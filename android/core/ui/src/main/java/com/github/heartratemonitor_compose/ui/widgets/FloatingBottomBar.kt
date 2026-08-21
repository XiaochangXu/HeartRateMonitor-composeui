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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.onGloballyPositioned
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

data class GlassTabItem(
    val iconRes: Int,
    val label: String
)

private val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

/**
 * 底层完整背景常驻 vibrancy + blur + lens + shadow；
 * 中层透明录制层 pressProgress 驱动 lens/highlight 增强；
 * 顶层滑动指示器 CombinedBackdrop 采样 + 速度感知形变。
 */
@Composable
fun FloatingBottomBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<GlassTabItem>,
    config: LiquidGlassConfig,
    interactive: () -> Boolean = { true },
    isTabSwitching: () -> Boolean = { false }
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

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                if (totalWidthPx == 0f) {
                    0f
                } else {
                    val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                    with(density) {
                        4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                    }
                }
            }
        }

        var currentIndex by remember(selectedTabIndex) { mutableIntStateOf(selectedTabIndex()) }

        
        val currentOnTabSelected by rememberUpdatedState(onTabSelected)
        // DampedDragAnimation / InteractiveHighlight 被 remember 缓存，enabled 闭包捕获首帧值，
        // 须经 rememberUpdatedState 转发，否则进入全屏后拖拽手势仍处于启用状态。
        val currentInteractive by rememberUpdatedState(interactive)

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
                    if (tabWidthPx > 0f) {
                        updateValue(
                            (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                                .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                        )
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                },
                enabled = { currentInteractive() }
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

        val interactiveHighlight =
            if (isBlurEnabled && supportsLens && tabWidthPx > 0f) {
                remember(animationScope, tabWidthPx) {
                    InteractiveHighlight(
                        animationScope = animationScope,
                        enabled = { currentInteractive() },
                        position = { size, _ ->
                            Offset(
                                if (isLtr) {
                                    (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                                } else {
                                    size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                                },
                                size.height / 2f
                            )
                        }
                    )
                }
            } else {
                null
            }

        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    val contentWidthPx = totalWidthPx - with(density) { 8f.dp.toPx() }
                    tabWidthPx = contentWidthPx / tabsCount
                }
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        // blur/lens 只由 isBlurEnabled 控制，不随 interactive() 关闭——
                        // 转场期间导航条保持玻璃质感（interactive 仅用于手势门控）
                        // 切 Tab 期间降级：保留 blur，去掉 vibrancy + lens（GPU 最贵的部分），
                        // 将三层 drawBackdrop 的 GPU 开销从 3×(vibrancy+blur+lens) 降到 1×blur
                        if (isBlurEnabled) {
                            val switching = isTabSwitching()
                            if (!switching) {
                                vibrancy()
                                blur(config.blurDp.dp.toPx())
                                if (supportsLens) {
                                    lens(config.distortionDp.dp.toPx(), config.distortionDp.dp.toPx())
                                }
                            } else {
                                blur(config.blurDp.dp.toPx())
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
            content = { TabsContent(tabs, selectedTabIndex, onTabSelected, interactive) }
        )

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
                            // 切 Tab 期间跳过中层 drawBackdrop：此层 alpha=0 不可见，
                            // 仅在非切 Tab 时为顶层 CombinedBackdrop 提供采样源
                            if (isBlurEnabled && !isTabSwitching()) {
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
                            Highlight.Default.copy(alpha = if (isBlurEnabled && !isTabSwitching()) progress else 0f)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight?.modifier ?: Modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = { TabsContent(tabs, selectedTabIndex, onTabSelected, interactive) }
            )
        }

        if (tabWidthPx > 0f) {
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    val contentWidth = totalWidthPx - with(density) { 8f.dp.toPx() }
                    val singleTabWidth = contentWidth / tabsCount
                    val progressOffset = dampedDragAnimation.value * singleTabWidth

                    translationX = if (isLtr) {
                        progressOffset + panelOffset
                    } else {
                        -progressOffset + panelOffset
                    }
                }
                .then(interactiveHighlight?.gestureModifier ?: Modifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { ContinuousCapsule },
                    effects = {
                        // 切 Tab 期间跳过顶层 drawBackdrop：pressProgress 接近 0 时
                        // lens 半径为 0（库内部直接 return），shadow/innerShadow alpha=0，
                        // 视觉上不可见，跳过可省去 CombinedBackdrop 的双重采样 + GPU effect
                        if (isBlurEnabled && supportsLens && !isTabSwitching()) {
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
                        Highlight.Default.copy(alpha = if (isBlurEnabled && !isTabSwitching()) progress else 0f)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = if (isBlurEnabled && !isTabSwitching()) progress else 0f)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8.dp * progress,
                            alpha = if (isBlurEnabled && !isTabSwitching()) progress else 0f
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
                .width(with(density) { ((totalWidthPx - 8f.dp.toPx()) / tabsCount).toDp() })
        )
        }
    }
}

@Composable
private fun RowScope.TabsContent(
    tabs: List<GlassTabItem>,
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    interactive: () -> Boolean
) {
    val scale = LocalFloatingBottomBarTabScale.current
    val currentIndex = selectedTabIndex()
    // enabled 门控：导航条隐藏（alpha=0 只影响绘制）时禁用 clickable，
    // 否则点击会在这里被消费，穿透不到下层二级页面的内容（如右下角 FAB）。
    val enabled = interactive()
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
                    enabled = enabled,
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

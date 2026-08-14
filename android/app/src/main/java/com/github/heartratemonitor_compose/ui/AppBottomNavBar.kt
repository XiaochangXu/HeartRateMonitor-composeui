package com.github.heartratemonitor_compose.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ui.animation.DampedDragAnimation
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassConfig
import com.github.heartratemonitor_compose.ui.widgets.FloatingBottomBar
import com.github.heartratemonitor_compose.ui.widgets.GlassTabItem
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private const val NAV_ITEM_DURATION = 200
private val NAV_ICON_SIZE = 24.dp

/**
 * 底部导航栏区域。
 *
 * 包含三层：
 * 1. 底部渐变遮罩（悬浮胶囊背后 + 系统导航条区域）
 * 2. 悬浮胶囊式底部导航（液态玻璃三层结构 / 简单 Surface 回退）
 *
 * 全屏心率模式时通过 alpha=0 隐藏，组合树常驻不销毁。
 */
@Composable
fun AppBottomNavBar(
    liquidGlassEnabled: Boolean,
    liquidBackdrop: Backdrop,
    liquidGlassConfig: LiquidGlassConfig,
    isFullScreenMode: Boolean,
    selectedPage: () -> Int,
    onTabSelected: (Int) -> Unit,
    navBarBottomInset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // ── 底部渐变遮罩（悬浮胶囊背后 + 系统导航条区域）──
        // 位于胶囊之下、Tab 内容之上：内容滚动到底部时平滑淡出到表面色。
        // 二级页面被 NavHost 覆盖，其底部渐变由 NavHost 之后的 inset 渐变层统一提供。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(navBarBottomInset + FLOATING_NAV_HEIGHT.dp + FLOATING_NAV_BOTTOM_MARGIN.dp)
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = if (isFullScreenMode) 0f else 1f }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                )
        )

        // ── 悬浮胶囊式底部导航（液态玻璃三层结构 / 简单回退）──
        // 组合树常驻不销毁：全屏时 alpha=0 隐藏，避免进出全屏时重建组合树导致
        // Backdrop/GPU 资源重新分配、手势协程重启、WindowInsets 抖动叠加。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = if (isFullScreenMode) 0f else 1f }
                .padding(horizontal = 16.dp)
                .padding(bottom = navBarBottomInset + FLOATING_NAV_BOTTOM_MARGIN.dp)
        ) {
            if (liquidGlassEnabled) {
                // 三层液态玻璃：背景 + 透明录制层 + 滑动指示器（含拖拽切换、跟随高光、速度形变）
                val tabLabels = listOf(
                    stringResource(R.string.nav_home),
                    stringResource(R.string.nav_history),
                    stringResource(R.string.nav_favorite),
                    stringResource(R.string.nav_settings)
                )
                val glassTabs = remember(tabLabels) {
                    listOf(
                        GlassTabItem(R.drawable.ic_tab_home, tabLabels[0]),
                        GlassTabItem(R.drawable.ic_tab_history, tabLabels[1]),
                        GlassTabItem(R.drawable.ic_tab_favorite, tabLabels[2]),
                        GlassTabItem(R.drawable.ic_tab_settings, tabLabels[3])
                    )
                }
                FloatingBottomBar(
                    backdrop = liquidBackdrop,
                    selectedTabIndex = selectedPage,
                    onTabSelected = { index -> onTabSelected(index) },
                    tabs = glassTabs,
                    config = liquidGlassConfig,
                    interactive = { !isFullScreenMode }
                )
            } else {
                // 简单回退：普通 Surface + 常驻 label，复用液态玻璃同款拖拽指示器手势
                SurfaceFallbackNav(
                    selectedPage = selectedPage,
                    onTabSelected = onTabSelected,
                    interactive = { !isFullScreenMode }
                )
            }
        }
    }
}

/**
 * 非液态玻璃回退模式的底部导航（复用 [DampedDragAnimation] 拖拽切换）。
 *
 * 结构与液态玻璃模式对齐：背景 Surface → 常驻 label 内容 → 顶层滑动指示器（承载拖拽手势）。
 * 指示器位于内容之上（液态玻璃模式同款层级），保证 Initial pass 拖拽手势不被 item 的
 * clickable 拦截；点按仍由 item clickable 处理（inspectDragGestures 不消费事件，二者共存）。
 */
@Composable
private fun SurfaceFallbackNav(
    selectedPage: () -> Int,
    onTabSelected: (Int) -> Unit,
    interactive: () -> Boolean
) {
    val tabsCount = 4
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    BoxWithConstraints(contentAlignment = Alignment.CenterStart) {
        val tabWidth = constraints.maxWidth.toFloat() / tabsCount

        // 拖动时整体微移反馈（与液态玻璃模式同参数）
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        var currentIndex by remember(selectedPage) { mutableIntStateOf(selectedPage()) }
        val currentOnTabSelected by rememberUpdatedState(onTabSelected)
        // DampedDragAnimation 被 remember 缓存，需用 rememberUpdatedState 避免 enabled 闭包
        // 捕获过时的 isFullScreenMode（全屏隐藏时手势应禁用，见 inspectDragGestures 注释）
        val currentInteractive by rememberUpdatedState(interactive)

        val dampedDragAnimation = remember(animationScope, density) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedPage().toFloat(),
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
                enabled = { currentInteractive() }
            )
        }

        // 外部切换（点击 item / Pager 滑动）时指示器跟随归位
        LaunchedEffect(selectedPage) {
            snapshotFlow { selectedPage() }.collectLatest { index ->
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

        // ── 背景 ──
        Surface(
            modifier = Modifier
                .graphicsLayer { translationX = panelOffset }
                .fillMaxWidth()
                .height(FLOATING_NAV_HEIGHT.dp),
            shape = ContinuousCapsule,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {}

        // ── 中层：滑动指示器视觉层（选中色块画在内容之下，不盖图标文字）──
        // 显式固定高度：BoxWithConstraints 继承全屏 maxHeight，fillMaxHeight() 会撑爆容器。
        // 本层不承载手势：手势在 clickable 之下时拖拽会被上层点按手势压制，
        // 手势另设顶层透明层承载（与液态玻璃模式"指示器在顶层承载手势"同构）。
        Box(
            Modifier
                .height(FLOATING_NAV_HEIGHT.dp)
                .fillMaxWidth(1f / tabsCount)
                .graphicsLayer {
                    translationX = if (isLtr) {
                        dampedDragAnimation.value * tabWidth + panelOffset
                    } else {
                        size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    }
                    scaleX = dampedDragAnimation.scaleX
                    scaleY = dampedDragAnimation.scaleY
                    val velocity = dampedDragAnimation.velocity / 10f
                    scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                    scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 4.dp)
                    .clip(ContinuousCapsule)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            )
        }

        // ── 内容层：图标 + 常驻 label（画在指示器之上）──
        Row(
            modifier = Modifier
                .graphicsLayer { translationX = panelOffset }
                .fillMaxWidth()
                .height(FLOATING_NAV_HEIGHT.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CapsuleNavItem(
                selected = currentIndex == 0,
                onClick = remember(onTabSelected) { { onTabSelected(0) } },
                iconRes = R.drawable.ic_tab_home,
                label = stringResource(R.string.nav_home),
                modifier = Modifier.weight(1f)
            )
            CapsuleNavItem(
                selected = currentIndex == 1,
                onClick = remember(onTabSelected) { { onTabSelected(1) } },
                iconRes = R.drawable.ic_tab_history,
                label = stringResource(R.string.nav_history),
                modifier = Modifier.weight(1f)
            )
            CapsuleNavItem(
                selected = currentIndex == 2,
                onClick = remember(onTabSelected) { { onTabSelected(2) } },
                iconRes = R.drawable.ic_tab_favorite,
                label = stringResource(R.string.nav_favorite),
                modifier = Modifier.weight(1f)
            )
            CapsuleNavItem(
                selected = currentIndex == 3,
                onClick = remember(onTabSelected) { { onTabSelected(3) } },
                iconRes = R.drawable.ic_tab_settings,
                label = stringResource(R.string.nav_settings),
                modifier = Modifier.weight(1f)
            )
        }

        // ── 顶层：透明手势层（跟随指示器位置，承载拖拽）──
        // 必须在内容层之上：液态玻璃模式的指示器即在顶层承载手势（设备上已验证），
        // 本层透明且不消费事件，clickable 点按仍能穿透到达下层（与玻璃模式同机制）。
        // graphicsLayer 在手势 modifier 之外（左侧），命中区跟随 translationX 位移，
        // 否则手势会钉在布局原点，造成拖拽错位与点按竞态。
        Box(
            Modifier
                .height(FLOATING_NAV_HEIGHT.dp)
                .fillMaxWidth(1f / tabsCount)
                .graphicsLayer {
                    translationX = if (isLtr) {
                        dampedDragAnimation.value * tabWidth + panelOffset
                    } else {
                        size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    }
                }
                .then(dampedDragAnimation.modifier)
        )
    }
}

/**
 * 胶囊导航栏单项（非液态玻璃回退模式使用）。
 * 选中底色由滑动指示器提供（指示器位于内容层之下），本项自身背景保持透明。
 */
@Composable
private fun CapsuleNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing),
        label = "capsuleItemColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing),
        label = "capsuleTextColor"
    )
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            ),
        shape = ContinuousCapsule,
        color = Color.Transparent
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(NAV_ICON_SIZE)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

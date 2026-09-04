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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
 * 底部 inset 用 [WindowInsets.navigationBars] 的布局修饰符（windowInsetsPadding/
 * windowInsetsBottomHeight）实时读取：冷启动首次组合时窗口 insets 尚未分发（=0），
 * 若在组合期取值（asPaddingValues）会拿到 0 且 insets 就绪后不自动刷新，
 * 导致导航条贴底——布局修饰符在布局时读取，insets 变化自动重新布局。
 */
@Composable
fun AppBottomNavBar(
    liquidGlassEnabled: Boolean,
    liquidBackdrop: Backdrop,
    liquidGlassConfig: LiquidGlassConfig,
    selectedPage: () -> Int,
    onTabSelected: (Int) -> Unit,
    isTabSwitching: () -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    // 二级页已独立成 Activity，导航条只在 Tab 宿主渲染，恒可交互
    val interactive = { true }
    Box(modifier = modifier) {
        // 底部渐变背景由 AppRoot 统一提供（windowInsetsBottomHeight 渐变层），
        // 导航条自身不再重复绘制背景渐变——否则两层叠加会导致底部区域
        // 看起来被一层淡色背景覆盖。导航条内容悬浮于渐变之上。

        // 内容：底部 inset（实时）+ margin 之上悬浮
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp)
                .padding(bottom = FLOATING_NAV_BOTTOM_MARGIN.dp)
        ) {
            if (liquidGlassEnabled) {
                val tabLabels = listOf(
                    stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.nav_home),
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
                    onTabSelected = onTabSelected,
                    tabs = glassTabs,
                    config = liquidGlassConfig,
                    interactive = interactive,
                    isTabSwitching = isTabSwitching
                )
            } else {
                // 简单回退：普通 Surface + 常驻 label，复用液态玻璃同款拖拽指示器手势
                SurfaceFallbackNav(
                    selectedPage = selectedPage,
                    onTabSelected = onTabSelected,
                    interactive = interactive
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
        // DampedDragAnimation 被 remember 缓存，需用 rememberUpdatedState 避免 enabled 闭包捕获过时的 interactive
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

        Surface(
            modifier = Modifier
                .graphicsLayer { translationX = panelOffset }
                .fillMaxWidth()
                .height(FLOATING_NAV_HEIGHT.dp),
            shape = ContinuousCapsule,
            color = MaterialTheme.colorScheme.surfaceBright
        ) {}

        // 显式固定高度：BoxWithConstraints 继承全屏 maxHeight，fillMaxHeight() 会撞爆容器。
        // 本层不承载手势：手势在 clickable 之下时拖拽会被上层点按手势压制，
        // 手势另设顶层透明层承载。
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
                enabled = interactive(),
                iconRes = R.drawable.ic_tab_home,
                label = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.nav_home),
                modifier = Modifier.weight(1f)
            )
            CapsuleNavItem(
                selected = currentIndex == 1,
                onClick = remember(onTabSelected) { { onTabSelected(1) } },
                enabled = interactive(),
                iconRes = R.drawable.ic_tab_history,
                label = stringResource(R.string.nav_history),
                modifier = Modifier.weight(1f)
            )
            CapsuleNavItem(
                selected = currentIndex == 2,
                onClick = remember(onTabSelected) { { onTabSelected(2) } },
                enabled = interactive(),
                iconRes = R.drawable.ic_tab_favorite,
                label = stringResource(R.string.nav_favorite),
                modifier = Modifier.weight(1f)
            )
            CapsuleNavItem(
                selected = currentIndex == 3,
                onClick = remember(onTabSelected) { { onTabSelected(3) } },
                enabled = interactive(),
                iconRes = R.drawable.ic_tab_settings,
                label = stringResource(R.string.nav_settings),
                modifier = Modifier.weight(1f)
            )
        }

        // 必须在内容层之上：液态玻璃模式的指示器即在顶层承载手势（设备上已验证）。
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

@Composable
private fun CapsuleNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
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
                enabled = enabled,
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

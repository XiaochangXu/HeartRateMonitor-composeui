<<<<<<< HEAD
package com.github.heartratemonitor_compose.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
=======
﻿package com.github.heartratemonitor_compose.ui

import android.content.Context
import android.content.Intent
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
<<<<<<< HEAD
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
=======
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
<<<<<<< HEAD
import androidx.compose.runtime.DisposableEffect
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
<<<<<<< HEAD
import androidx.lifecycle.compose.collectAsStateWithLifecycle
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.ui.alarm.HeartRateAlarmScreen
import com.github.heartratemonitor_compose.ui.favorite.FavoriteDevicesScreen
import com.github.heartratemonitor_compose.ui.history.ChartScreen
import com.github.heartratemonitor_compose.ui.history.HistoryScreen
<<<<<<< HEAD
import com.github.heartratemonitor_compose.ui.main.AppStatus
import com.github.heartratemonitor_compose.ui.main.FullScreenHeartRate
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
import com.github.heartratemonitor_compose.ui.main.HomeScreen
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import com.github.heartratemonitor_compose.ui.server.ServerScreen
import com.github.heartratemonitor_compose.ui.settings.FairMemoryScreen
import com.github.heartratemonitor_compose.ui.settings.SettingsScreen
import com.github.heartratemonitor_compose.ui.webhook.WebhookScreen

// ── 转场动画 spec ──────────────────────────────────────────────
// 三层 overlay 架构：
//   底层 = Tab 页内容（固定 padding，永不重绘）
//   中层 = NavigationBar 浮动 overlay
//   顶层 = 二级页面覆盖式从右滑入
//
// 原页面保持不动，目标页面覆盖式弹出——符合 iOS / Material 3 push 风格。
// 1. Tab↔Tab（200ms）：横向 slide，方向由 tabOrder 判断
// 2. 进入二级页面（300ms）：二级页从右滑入覆盖全屏，Tab 页和 NavigationBar 完全不动（被覆盖）
// 3. 返回（300ms）：二级页向右滑出，露出 Tab 页和 NavigationBar
//
// 可中断性由 AnimatedContent 保证：targetState 变化时取消当前动画，
// 新动画从「当前进度」继续，无跳变。
private const val TAB_SLIDE_DURATION = 200
private const val SECONDARY_SLIDE_DURATION = 300
<<<<<<< HEAD
private const val NAV_BAR_HEIGHT = 64
=======
private const val NAV_BAR_HEIGHT = 80
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
private const val NAV_ITEM_DURATION = 200
// MD3 NavigationBar 胶囊指示器尺寸规范
private val NAV_INDICATOR_WIDTH = 64.dp
private val NAV_INDICATOR_HEIGHT = 32.dp
private val NAV_INDICATOR_CORNER = 16.dp
private val NAV_ICON_SIZE = 24.dp

// ── 页面状态（替代 NavHost 路由）──────────────────────────────────
sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
    object History : Screen()
    data class Chart(val sessionId: Long) : Screen()
    object Favorite : Screen()
    object Alarm : Screen()
    object Server : Screen()
    object Webhook : Screen()
    object FairMemory : Screen()
}

/** 底部导航 Tab 页（home / settings） */
private fun Screen.isTab(): Boolean = this is Screen.Home || this is Screen.Settings

/** 二级页面（从 Tab 页进入的子页面） */
private fun Screen.isSecondary(): Boolean = !isTab()

/** Tab 顺序：用于判断 Tab↔Tab 切换方向（前进/后退），不依赖外部状态 */
private fun Screen.tabOrder(): Int = when (this) {
    is Screen.Home -> 0
    is Screen.Settings -> 1
    else -> -1
}

/** 字符串路由名 → Screen（供 SettingsScreen.onNavigate(String) 调用） */
private fun String.toScreen(): Screen = when (this) {
    "favorite" -> Screen.Favorite
    "alarm" -> Screen.Alarm
    "server" -> Screen.Server
    "webhook" -> Screen.Webhook
    "fair_memory" -> Screen.FairMemory
    "history" -> Screen.History
    else -> Screen.Home
}

/**
 * 单 Activity 架构的根 Composable。三层 overlay 堆叠：
 *
 * 1. **底层 — Tab 页内容**（[AnimatedContent] targetState = [currentTab]）
 *    进入二级页面时 currentTab 不变，底层完全不重组/重绘，原页面纹丝不动。
 *    Tab↔Tab 切换在此层用横向 slide（200ms）。
 *
 * 2. **中层 — NavigationBar** 浮动 overlay（[Box] align BottomCenter）
 *    不在 Scaffold bottomBar 中，因此其显隐不影响内容区域尺寸。
 *    二级页面进入时被顶层覆盖，无需动画。
 *
 * 3. **顶层 — 二级页面**（[AnimatedContent] targetState = secondaryStack.lastOrNull()）
 *    null = 在 Tab 页（渲染空内容，透明，露出底层）。
 *    进入二级页面时从右滑入覆盖全屏（300ms）；返回时向右滑出。
 *
 * 签名仅保留 3 个跨 Activity 回调：
 * - [onToggleFloatingWindow] — HomeScreen 调用
 * - [onMediaProjectionRequest] — SettingsScreen 调用，由 MainActivity 持有 launcher
 * - [onOpenExternal] — SettingsScreen GitHub 外链 / 系统设置 Intent
 */
@Composable
fun AppRoot(
    onToggleFloatingWindow: () -> Unit,
    onMediaProjectionRequest: (Intent) -> Unit,
    onOpenExternal: (Intent) -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember(context) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }
    val db = remember(context) { AppDatabase.getDatabase(context) }

    // MainViewModel 绑定到 Activity 级 ViewModelStoreOwner，
    // 与 MainActivity.onServiceConnected 中 ViewModelProvider(this) 获取的是同一实例。
    val mainViewModel: MainViewModel = viewModel()

<<<<<<< HEAD
    // ── 全屏心率模式 ──
    // 状态提升到 AppRoot：FullScreenHeartRate 需覆盖在 NavigationBar 之上，
    // 必须由 AppRoot 统一管理，在全屏时隐藏 NavigationBar。
    var isFullScreenMode by remember { mutableStateOf(false) }
    val heartRate by mainViewModel.heartRate.collectAsStateWithLifecycle()
    val appStatus by mainViewModel.appStatus.collectAsStateWithLifecycle()

    // 全屏模式：强制横屏；退出时恢复方向（configChanges 已声明，不重建 Activity）
    DisposableEffect(isFullScreenMode) {
        val activity = context as? Activity
        if (isFullScreenMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 断开连接时自动退出全屏
    LaunchedEffect(appStatus) {
        if (appStatus != AppStatus.CONNECTED && isFullScreenMode) {
            isFullScreenMode = false
        }
    }

=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
    // ── 状态管理（三层 overlay）──
    // currentTab：当前 Tab 页（Home/Settings），驱动底层 AnimatedContent。
    //   进入二级页面时 currentTab 不变 → 底层不重组 → 原页面纹丝不动。
    // secondaryStack：二级页面栈，lastOrNull() 为当前二级页面（null=在 Tab 页）。
    //   驱动顶层 AnimatedContent：null→二级页=进入，二级页→null=返回。
    var currentTab by remember { mutableStateOf<Screen>(Screen.Home) }
    val secondaryStack = remember { mutableStateListOf<Screen>() }

    // 二级页面过渡进度：0=在 Tab 页，1=二级页面完全展开。
    // 使用 Animatable 替代 animateFloatAsState，实现协程级精确控制：
    //   - 进入时立即设置 displayedSecondary，动画与内容切换同步
    //   - 返回时先动画滑出，完成后才清除 displayedSecondary（保证旧内容全程可见）
    //   - 中断续传：快速操作时 animateTo 被取消，新动画从当前进度继续
    // 驱动底层 Tab 内容层的视差上抬 + 缩放 + 实时模糊，以及顶层二级页面的 translationY 滑动。
    val secondaryProgress = remember { Animatable(0f) }
    // 实际显示的二级页面（返回动画期间保持旧内容，动画结束后才置 null）
    var displayedSecondary by remember { mutableStateOf<Screen?>(null) }

    LaunchedEffect(secondaryStack.lastOrNull()) {
        val target = secondaryStack.lastOrNull()
        if (target != null) {
            // 进入二级页面：先设置内容，再动画滑入
            displayedSecondary = target
            secondaryProgress.animateTo(1f, tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing))
        } else {
            // 返回 Tab 页：先动画滑出，完成后清除内容
            secondaryProgress.animateTo(0f, tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing))
            if (secondaryStack.isEmpty()) {
                displayedSecondary = null
            }
        }
    }

    fun navigate(screen: Screen) {
        if (screen.isTab()) {
            // Tab 切换：清空二级栈，切换 Tab（底层 AnimatedContent 横向 slide）
            secondaryStack.clear()
            if (screen != currentTab) currentTab = screen
        } else {
            // 进入二级页面：压入二级栈（顶层 AnimatedContent 从右滑入）
            secondaryStack.add(screen)
        }
    }

    fun popBack(): Boolean {
        if (secondaryStack.isNotEmpty()) {
            secondaryStack.removeLast()
            return true
        }
        return false
    }

    // 二级页面按返回键 pop；Tab 页让系统处理（退出应用）
    BackHandler(enabled = secondaryStack.isNotEmpty()) { popBack() }

<<<<<<< HEAD
    // 系统手势条/导航栏 inset：内容与底部 NavigationBar 都需避开此区域
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
    Box(modifier = Modifier.fillMaxSize()) {
        // ── 底层：两个 Tab 页常驻 + offset 平移动画 ──
        // 不再用 AnimatedContent：避免每次 Tab 切换重新组合整个页面
        //   （remember/LaunchedEffect/DisposableEffect 全部重新执行，导致首帧延迟 → 顿挫感）
        // 改为两个 Tab 页同时存在，切换时只动画 Modifier.offset 的 x 偏移：
        //   - 无 SubcomposeLayout 子组合开销
        //   - 无 DisposableEffect onDispose/re-register 反复触发
        //   - 速度连续 + 可中断（animateFloatAsState 中断时从当前进度继续）
        //   - 偏移出屏幕的页面不参与 hit-test，自然不接收触摸事件
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
<<<<<<< HEAD
                .padding(bottom = NAV_BAR_HEIGHT.dp + navBarBottomInset)
=======
                .padding(bottom = NAV_BAR_HEIGHT.dp)
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
                .graphicsLayer {
                    val progress = secondaryProgress.value
                    // 原背景跟随二级页面上移而上抬（视差），轻微缩放增强景深
                    translationY = -progress * size.height * 0.15f
                    scaleX = 1f - progress * 0.04f
                    scaleY = 1f - progress * 0.04f
                    // API 31+ 实时模糊；低版本跳过（仅缩放+位移）
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && progress > 0f) {
                        renderEffect = RenderEffect.createBlurEffect(
                            progress * 18f,
                            progress * 18f,
                            Shader.TileMode.DECAL
                        ).asComposeRenderEffect()
                    }
                }
        ) {
            val screenWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
            // 当前 Tab 归一化位置：Home=0, Settings=1（复用 tabOrder() 扩展）
            val targetTabPosition = currentTab.tabOrder().toFloat()
            val animatedTabPosition by animateFloatAsState(
                targetValue = targetTabPosition,
                animationSpec = tween(TAB_SLIDE_DURATION, easing = FastOutSlowInEasing),
                label = "tabSlide"
            )

            // Home 层：position=0 时 offset=0，position=1 时 offset=-width（向左滑出屏幕）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            x = (-animatedTabPosition * screenWidthPx).roundToInt(),
                            y = 0
                        )
                    }
            ) {
                HomeScreen(
                    viewModel = mainViewModel,
                    onOpenHistory = { navigate(Screen.History) },
<<<<<<< HEAD
                    onToggleFloatingWindow = onToggleFloatingWindow,
                    onEnterFullScreen = { isFullScreenMode = true }
=======
                    onToggleFloatingWindow = onToggleFloatingWindow
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
                )
            }

            // Settings 层：position=0 时 offset=+width（屏幕外右侧待滑入），position=1 时 offset=0
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            x = ((1f - animatedTabPosition) * screenWidthPx).roundToInt(),
                            y = 0
                        )
                    }
            ) {
                SettingsScreen(
                    sharedPreferences = sharedPreferences,
                    onNavigateBack = { popBack() },
                    onNavigate = { route -> navigate(route.toScreen()) },
                    onOpenExternal = onOpenExternal,
                    onRequestMediaProjection = onMediaProjectionRequest,
                    showToast = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // ── 中层：NavigationBar 浮动 overlay（覆盖在 Tab 页内容底部）──
        // 不在 Scaffold bottomBar 中，显隐不影响内容区域尺寸。
        // 二级页面进入时被顶层覆盖，无需 AnimatedVisibility。
<<<<<<< HEAD
        // 全屏心率模式时隐藏 NavigationBar。
        // 沉浸式：容器延伸到屏幕底部（外层不加 navigationBarsPadding），
        // 高度 = NAV_BAR_HEIGHT + 系统导航栏 inset；M3 默认 windowInsets 把内容上推 inset，
        // 导航项实际活动空间 = NAV_BAR_HEIGHT，居中不受影响，系统手势条区域被容器背景填充。
        if (!isFullScreenMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                NavigationBar(
                    modifier = Modifier
                        .height(NAV_BAR_HEIGHT.dp + navBarBottomInset)
                ) {
                    AnimatedNavItem(
                        selected = currentTab is Screen.Home,
                        onClick = { navigate(Screen.Home) },
                        iconRes = R.drawable.ic_home_symbol,
                        label = "首页"
                    )
                    AnimatedNavItem(
                        selected = currentTab is Screen.Settings,
                        onClick = { navigate(Screen.Settings) },
                        iconRes = R.drawable.ic_settings_symbol,
                        label = "设置"
                    )
                }
            }
=======
        NavigationBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(NAV_BAR_HEIGHT.dp)
        ) {
            AnimatedNavItem(
                selected = currentTab is Screen.Home,
                onClick = { navigate(Screen.Home) },
                iconRes = R.drawable.ic_home_symbol,
                label = "首页"
            )
            AnimatedNavItem(
                selected = currentTab is Screen.Settings,
                onClick = { navigate(Screen.Settings) },
                iconRes = R.drawable.ic_settings_symbol,
                label = "设置"
            )
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
        }

        // ── 顶层：二级页面（GPU 合成 translationY 滑动 + Crossfade 内容切换）──
        // 性能优化：用 graphicsLayer { translationY } 替代 AnimatedContent 的 slideInVertically：
        //   - slideInVertically 每帧触发 measure/layout（CPU 密集），导致掉帧
        //   - graphicsLayer translationY 纯 GPU 合成，零布局开销
        //   - Crossfade 仅动画 alpha（GPU），内容切换无布局开销
        //   - 动画由 secondaryProgress 单一驱动，底层模糊与顶层滑动完美同步
        // displayedSecondary 在返回动画期间保持旧内容，动画结束后才置 null，
        // 保证旧页面完整滑出而非「直接消失」。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = secondaryProgress.value
                    translationY = (1f - progress) * size.height
                    // 完全在 Tab 页时跳过渲染
                    alpha = if (progress > 0f) 1f else 0f
                }
        ) {
            Crossfade(
                targetState = displayedSecondary,
                animationSpec = tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing),
                label = "secondaryContent"
            ) { secondary ->
                if (secondary != null) {
                    when (secondary) {
                        Screen.History -> HistoryScreen(
                            db = db,
                            onNavigateBack = { popBack() },
                            onNavigateToChart = { sessionId ->
                                navigate(Screen.Chart(sessionId))
                            }
                        )
                        is Screen.Chart -> ChartScreen(
                            sessionId = secondary.sessionId,
                            onNavigateBack = { popBack() }
                        )
                        Screen.Favorite -> FavoriteDevicesScreen(
                            favoriteDeviceDao = db.favoriteDeviceDao(),
                            sharedPreferences = sharedPreferences,
                            onNavigateBack = { popBack() }
                        )
                        Screen.Alarm -> HeartRateAlarmScreen(
                            sharedPreferences = sharedPreferences,
                            onNavigateBack = { popBack() }
                        )
                        Screen.Server -> ServerScreen(
                            onNavigateBack = { popBack() },
                            sharedPreferences = sharedPreferences
                        )
                        Screen.Webhook -> WebhookScreen(
                            onNavigateBack = { popBack() },
                            context = context
                        )
                        Screen.FairMemory -> FairMemoryScreen(
                            onNavigateBack = { popBack() }
                        )
                        else -> {}
                    }
                }
            }
        }
<<<<<<< HEAD

        // ── 最顶层：全屏心率模式覆盖层 ──
        // 渲染在 NavigationBar 和二级页面之上，纯黑背景覆盖一切，
        // 仅显示自适应放大的爱心和心率数值。
        if (isFullScreenMode) {
            FullScreenHeartRate(
                heartRate = heartRate,
                onExit = { isFullScreenMode = false }
            )
        }
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
    }
}

/**
 * 自定义底部导航 Item（M3 NavigationBar 风格）。
 *
 * - **未选中**：图标垂直居中，无背景胶囊，无文字，颜色 onSurfaceVariant。
 * - **选中**：图标被 secondaryContainer 胶囊包裹，下方展开文字，图标色切到 onSecondaryContainer。
 *
 * 胶囊指示器遵循 M3 规范：64dp × 32dp，圆角 16dp（pill 形状）。
 * 选中时胶囊宽度从 32dp（圆形包裹图标）扩展到 64dp（完整 pill），配合颜色淡入，
 * 营造从中心向两侧展开的视觉效果。文字 AnimatedVisibility 同步展开作为次要反馈。
 *
 * 在 [NavigationBar] 的 RowScope 内用 weight(1f) 平分宽度，fillMaxHeight 撑满
<<<<<<< HEAD
 * 容器高度（NAV_BAR_HEIGHT + 系统导航栏 inset）。M3 NavigationBar 默认 windowInsets
 * 把 Row 内容上推 inset，因此 fillMaxHeight 实际可用空间 = NAV_BAR_HEIGHT，居中不受影响。
 * 点击用 indication = null 的 clickable，不显示长方形 ripple 覆盖，
 * 由胶囊指示器 + 文字展开动画作为选中反馈。
=======
 * 80dp 容器高度。点击用 indication = null 的 clickable，不显示长方形 ripple 覆盖，
 * 由胶囊指示器 + 文字展开动画作为选中反馈。NavigationBar 容器自动处理 navigationBars inset。
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
 */
@Composable
private fun RowScope.AnimatedNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    label: String
) {
    // 胶囊背景色：选中时 secondaryContainer，未选中透明（淡入淡出）
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else Color.Transparent,
        animationSpec = tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing),
        label = "navIndicatorColor"
    )
    // 图标颜色：选中时 onSecondaryContainer（在胶囊内），未选中 onSurfaceVariant
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing),
        label = "navItemColor"
    )
    // 胶囊宽度动画：32dp（圆形包裹图标）→ 64dp（M3 标准 pill 宽度）
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) NAV_INDICATOR_WIDTH else NAV_INDICATOR_HEIGHT,
        animationSpec = tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing),
        label = "navIndicatorWidth"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 胶囊指示器：固定高度 32dp，宽度 32→64dp 动画，圆角 16dp 形成完整 pill
            Box(
                modifier = Modifier
                    .width(indicatorWidth)
                    .height(NAV_INDICATOR_HEIGHT)
                    .background(
                        color = indicatorColor,
                        shape = RoundedCornerShape(NAV_INDICATOR_CORNER)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(NAV_ICON_SIZE)
                )
            }
            AnimatedVisibility(
                visible = selected,
                enter = expandVertically(tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing)) +
                    fadeIn(tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing)) +
                    fadeOut(tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing))
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = iconColor
                )
            }
        }
    }
}

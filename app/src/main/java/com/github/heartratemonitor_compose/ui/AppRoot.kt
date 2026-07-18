package com.github.heartratemonitor_compose.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.ui.alarm.HeartRateAlarmScreen
import com.github.heartratemonitor_compose.ui.favorite.FavoriteDevicesScreen
import com.github.heartratemonitor_compose.ui.history.ChartScreen
import com.github.heartratemonitor_compose.ui.history.HistoryScreen
import com.github.heartratemonitor_compose.ui.main.AppStatus
import com.github.heartratemonitor_compose.ui.main.FullScreenHeartRate
import com.github.heartratemonitor_compose.ui.main.DevicesScreen
import com.github.heartratemonitor_compose.ui.main.HomeScreen
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import com.github.heartratemonitor_compose.ui.server.ServerScreen
import com.github.heartratemonitor_compose.ui.settings.FairMemoryScreen
import com.github.heartratemonitor_compose.ui.settings.SettingsScreen
import com.github.heartratemonitor_compose.ui.theme.ThemeSettingsScreen
import com.github.heartratemonitor_compose.ui.webhook.WebhookScreen
import kotlinx.coroutines.launch

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
// 悬浮胶囊式底部导航：高度 + 底部留白
private const val FLOATING_NAV_HEIGHT = 64
private const val FLOATING_NAV_BOTTOM_MARGIN = 8
private const val NAV_ITEM_DURATION = 200
// 胶囊内导航项指示器尺寸
private val NAV_INDICATOR_HEIGHT = 40.dp
private val NAV_INDICATOR_CORNER = 20.dp
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
    object Theme : Screen()
    object Devices : Screen()
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
    "theme" -> Screen.Theme
    "devices" -> Screen.Devices
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

    // ── 全屏心率模式 ──
    // 状态提升到 AppRoot：FullScreenHeartRate 需覆盖在 NavigationBar 之上，
    // 必须由 AppRoot 统一管理，在全屏时隐藏 NavigationBar。
    var isFullScreenMode by remember { mutableStateOf(false) }
    val heartRate by mainViewModel.heartRate.collectAsStateWithLifecycle()
    val appStatus by mainViewModel.appStatus.collectAsStateWithLifecycle()

    // ── 悬浮窗开关状态（底部圆形按钮）──
    // 状态提升到 AppRoot：圆形按钮在底部导航栏中，需监听 SharedPreferences 变化
    var floatingWindowEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("floating_window_enabled", false))
    }
    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "floating_window_enabled") {
                floatingWindowEnabled = sharedPreferences.getBoolean("floating_window_enabled", false)
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

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

    // 系统手势条/导航栏 inset：内容与底部 NavigationBar 都需避开此区域
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 胶囊导航栏显隐：向下滚动隐藏，向上滚动显示
    // navBarHideProgress: 0f = 完全显示, 1f = 完全隐藏
    //
    // 实现策略（Google AppBar 标准做法）：
    //   - 滚动过程中：snapTo 跟手更新 progress（严格跟随手指位移）
    //   - 松手后：animateTo 平滑完成到 0 或 1
    //     · progress > 0.5 → 1f（完全隐藏）
    //     · progress < 0.5 → 0f（完全显示）
    //
    // 动画规范遵循 Material 3 官方推荐：
    //   - 时长：300ms（md-sys-motion-duration-medium2）
    //   - 缓动曲线：emphasized（cubic-bezier(0.2, 0, 0, 1)）
    //     Compose 内置 FastOutSlowInEasing 最接近此曲线
    val navBarHideProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    // 胶囊完全隐藏所需的总位移（px）：胶囊高度 + 底部边距 + 系统导航栏 inset
    val totalHideDistance = with(LocalDensity.current) {
        (FLOATING_NAV_HEIGHT.dp + FLOATING_NAV_BOTTOM_MARGIN.dp + navBarBottomInset).toPx()
    }
    // M3 emphasized 缓动曲线对应 Compose 的 FastOutSlowInEasing
    val navBarAnimationSpec = tween<Float>(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    )
    val nestedScrollConnection = remember(currentTab) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 仅在 Settings Tab 触发胶囊隐藏/显示；
                // Home Tab（首页）保持导航栏固定，不响应滚动
                if (currentTab !is Screen.Settings) return Offset.Zero
                // 忽略 overscroll 副作用（SideEffect）：
                // 当 LazyColumn 滚动到顶部/底部时，系统会触发边缘拉伸弹性动画，
                // 这些 delta 不应参与胶囊显隐计算，否则会导致 progress 抖动 + 弹跳
                if (source == NestedScrollSource.SideEffect) return Offset.Zero
                val delta = available.y
                if (delta != 0f) {
                    // 跟手更新：delta<0（向下滚）→ progress 增大；delta>0（向上滚）→ progress 减小
                    val currentPx = navBarHideProgress.value * totalHideDistance
                    val newPx = (currentPx - delta).coerceIn(0f, totalHideDistance)
                    val newProgress = newPx / totalHideDistance
                    coroutineScope.launch { navBarHideProgress.snapTo(newProgress) }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 仅在 Settings Tab 处理
                if (currentTab !is Screen.Settings) return Offset.Zero
                // 忽略 overscroll 副作用
                if (source == NestedScrollSource.SideEffect) return Offset.Zero
                // 处理 LazyColumn 消费后剩余的 delta（fling 期间 / 已到边界后的剩余滚动）
                val delta = available.y
                if (delta != 0f) {
                    val currentPx = navBarHideProgress.value * totalHideDistance
                    val newPx = (currentPx - delta).coerceIn(0f, totalHideDistance)
                    val newProgress = newPx / totalHideDistance
                    coroutineScope.launch { navBarHideProgress.snapTo(newProgress) }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // 仅在 Settings Tab 处理松手动画
                if (currentTab !is Screen.Settings) return Velocity.Zero
                // 松手后平滑完成：根据当前进度决定最终状态
                val target = if (navBarHideProgress.value > 0.5f) 1f else 0f
                // 仅在未到达目标时启动动画，避免无意义的 300ms 动画
                if (navBarHideProgress.value != target) {
                    navBarHideProgress.animateTo(target, navBarAnimationSpec)
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        // 大背景使用 M3 surfaceDim：app 级背景色，比 surface 暗，与卡片 surfaceContainerHigh 形成色阶对比
        .background(MaterialTheme.colorScheme.surfaceDim)
        .nestedScroll(nestedScrollConnection)) {
        // ── 底层：两个 Tab 页常驻 + offset 平移动画 ──
        // 不再用 AnimatedContent：避免每次 Tab 切换重新组合整个页面
        //   （remember/LaunchedEffect/DisposableEffect 全部重新执行，导致首帧延迟 → 顿挫感）
        // 改为两个 Tab 页同时存在，切换时只动画 Modifier.offset 的 x 偏移：
        //   - 无 SubcomposeLayout 子组合开销
        //   - 无 DisposableEffect onDispose/re-register 反复触发
        //   - 速度连续 + 可中断（animateFloatAsState 中断时从当前进度继续）
        //   - 偏移出屏幕的页面不参与 hit-test，自然不接收触摸事件
        // 内容延伸到屏幕底部（iOS 风格），胶囊浮在内容之上；
        // 各 Tab 页内部自行在滚动内容末尾留出胶囊高度+边距，避免最后一项被遮挡
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
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
                    onNavigateToDevices = { navigate(Screen.Devices) },
                    onEnterFullScreen = { isFullScreenMode = true }
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
                    onNavigate = { route -> navigate(route.toScreen()) },
                    onOpenExternal = onOpenExternal,
                    onRequestMediaProjection = onMediaProjectionRequest,
                    showToast = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // ── 中层：悬浮胶囊式底部导航 ──
        // M3 扁平化：左侧胶囊（首页+设置）+ 右侧圆形（悬浮窗开关），分离不拼接
        // 无阴影无 tonal elevation，依靠 surfaceContainer 与 surfaceDim 背景的色阶差异体现层次
        if (!isFullScreenMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 向下滚动时隐藏，向上滚动时显示：根据 hideProgress 向下位移
                    // 使用 graphicsLayer.translationY 而非 offset {}：
                    //   - offset {} 的 lambda 在 layout 阶段执行，不追踪 snapshot state 读取
                    //   - graphicsLayer {} 的 block 会响应 snapshot state 变化（纯 GPU 合成，零布局开销）
                    .graphicsLayer {
                        translationY = navBarHideProgress.value * totalHideDistance
                    }
                    .padding(horizontal = 16.dp)
                    .padding(bottom = navBarBottomInset + FLOATING_NAV_BOTTOM_MARGIN.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 胶囊容器：首页 + 设置
                Surface(
                    modifier = Modifier.weight(1f).height(FLOATING_NAV_HEIGHT.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CapsuleNavItem(
                            selected = currentTab is Screen.Home,
                            onClick = { navigate(Screen.Home) },
                            iconRes = R.drawable.ic_home_symbol,
                            label = stringResource(R.string.nav_home),
                            modifier = Modifier.weight(1f)
                        )
                        CapsuleNavItem(
                            selected = currentTab is Screen.Settings,
                            onClick = { navigate(Screen.Settings) },
                            iconRes = R.drawable.ic_settings_symbol,
                            label = stringResource(R.string.nav_settings),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // 圆形：悬浮窗开关
                Surface(
                    modifier = Modifier.size(FLOATING_NAV_HEIGHT.dp),
                    shape = CircleShape,
                    color = if (floatingWindowEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainer,
                    onClick = onToggleFloatingWindow
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(
                                if (floatingWindowEnabled) R.drawable.ic_floating_window_on
                                else R.drawable.ic_floating_window_off
                            ),
                            contentDescription = stringResource(R.string.cd_toggle_floating_window),
                            tint = if (floatingWindowEnabled) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
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
                        Screen.Theme -> ThemeSettingsScreen(
                            onNavigateBack = { popBack() }
                        )
                        Screen.Devices -> DevicesScreen(
                            viewModel = mainViewModel,
                            onNavigateBack = { popBack() }
                        )
                        else -> {}
                    }
                }
            }
        }

        // ── 最顶层：全屏心率模式覆盖层 ──
        // 渲染在 NavigationBar 和二级页面之上，纯黑背景覆盖一切，
        // 仅显示自适应放大的爱心和心率数值。
        if (isFullScreenMode) {
            FullScreenHeartRate(
                heartRate = heartRate,
                onExit = { isFullScreenMode = false }
            )
        }
    }
}

/**
 * 悬浮胶囊内导航项（iOS 风格）。
 *
 * - **未选中**：仅图标，颜色 onSurfaceVariant。
 * - **选中**：图标 + 文字水平排列，被 secondaryContainer 圆角胶囊包裹。
 *   胶囊宽度随文字展开动画横向扩展，形成从中心向两侧展开的视觉效果。
 *
 * 选中态使用横向 expandHorizontally（而非 M3 的纵向 expandVertically），
 * 因为胶囊导航项的图标和文字是水平排列的，横向展开更自然。
 */
@Composable
private fun CapsuleNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else Color.Transparent,
        animationSpec = tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing),
        label = "capsuleIndicatorColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing),
        label = "capsuleItemColor"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(NAV_INDICATOR_HEIGHT)
                .background(
                    color = indicatorColor,
                    shape = RoundedCornerShape(NAV_INDICATOR_CORNER)
                )
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(NAV_ICON_SIZE)
            )
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing)) +
                    fadeIn(tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing)),
                exit = shrinkHorizontally(tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing)) +
                    fadeOut(tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing))
            ) {
                Text(
                    text = " $label",
                    style = MaterialTheme.typography.labelMedium,
                    color = iconColor,
                    maxLines = 1
                )
            }
        }
    }
}

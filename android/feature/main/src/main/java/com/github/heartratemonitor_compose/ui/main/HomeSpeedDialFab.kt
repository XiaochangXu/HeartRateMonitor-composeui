package com.github.heartratemonitor_compose.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.feature.main.R

/**
 * 首页 Speed Dial FAB 组：
 * 仅在已连接时显示，提供「进入全屏」与「断开连接」两个快捷动作。
 *
 * 内含展开状态管理（断开自动收起）与展开/收起时图标旋转 45° 的动画。
 *
 * @param modifier 调用方传入的对齐修饰（如 BoxScope 内的 align(BottomEnd)）
 * @param navBarInset 导航栏底部内边距，用于计算悬浮位置
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeSpeedDialFab(
    isConnected: Boolean,
    navBarInset: Dp,
    onEnterFullScreen: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var speedDialExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isConnected) {
        if (!isConnected) speedDialExpanded = false
    }
    // 展开/收起时图标旋转 45°（弹簧动画已移除）
    val iconRotation by animateFloatAsState(
        targetValue = if (speedDialExpanded) 45f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "speedDialIconRotation"
    )

    AnimatedVisibility(
        visible = isConnected,
        enter = scaleIn(initialScale = 0.6f) + fadeIn(),
        exit = scaleOut(targetScale = 0.6f) + fadeOut(),
        modifier = modifier
    ) {
        FloatingActionButtonMenu(
            modifier = Modifier.padding(
                end = 8.dp,
                bottom = navBarInset + 12.dp + 64.dp + 8.dp + 16.dp
            ),
            expanded = speedDialExpanded,
            button = {
                ToggleFloatingActionButton(
                    checked = speedDialExpanded,
                    onCheckedChange = { speedDialExpanded = !speedDialExpanded }
                ) {
                    // 用外部 tween 驱动的旋转值替代组件内置 checkedProgress 弹簧旋转
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.cd_speed_dial),
                        modifier = Modifier.graphicsLayer { rotationZ = iconRotation }
                    )
                }
            }
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    onEnterFullScreen()
                    speedDialExpanded = false
                },
                icon = { Icon(painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_fullscreen), contentDescription = null) },
                text = { Text(stringResource(R.string.enter_fullscreen)) }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    onDisconnect()
                    speedDialExpanded = false
                },
                icon = { Icon(painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_bluetooth_disabled), contentDescription = null) },
                text = { Text(stringResource(R.string.disconnect)) }
            )
        }
    }
}

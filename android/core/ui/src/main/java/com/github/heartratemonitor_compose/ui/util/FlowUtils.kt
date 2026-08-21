package com.github.heartratemonitor_compose.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 仅在 [isActive] 为 true 时收集 Flow，暂停期间保留 [initial] 值。
 *
 * 用于页面切到后台/二级页面时停止订阅心率、图表等高频更新，
 * 避免后台页面持续重组导致转场动画掉帧。
 */
@Composable
fun <T> Flow<T>.collectWhenActive(
    isActive: Boolean,
    initial: T
): State<T> {
    val state = remember { mutableStateOf(initial) }
    LaunchedEffect(isActive) {
        if (isActive) this@collectWhenActive.collect { state.value = it }
    }
    return state
}

/**
 * [StateFlow] 专用的 [collectWhenActive] 重载。
 *
 * 与 [Flow.collectWhenActive] 的唯一区别：初值取 [StateFlow.value]（ViewModel 当前
 * 已持有的真实状态），而非调用方传入的空默认值。这样页面从后台切回前台时
 * [remember] 块首次执行的初值即为真实数据，不会出现"空初始值 → 真实数据"
 * 的 1-2 帧跳变（如历史/收藏页空状态 icon 闪一下又消失）。
 *
 * 零额外开销：[StateFlow.value] 是原子读（纳秒级）；且 collect 首个发射值
 * 与初值相同，[mutableStateOf] 判等后不触发多余重组。
 */
@Composable
fun <T> StateFlow<T>.collectWhenActive(
    isActive: Boolean
): State<T> {
    val state = remember { mutableStateOf(value) }
    LaunchedEffect(isActive) {
        if (isActive) {
            // isActive 从 false→true 时，先同步刷新到 StateFlow 当前值：
            // 预组合期间 remember 读的 value 可能已过时（ViewModel 在后台
            // 已更新 StateFlow，但 remember 不会重读）。同步赋值后再 collect，
            // 确保进入前台首帧即为最新状态，避免显示过时的加载指示器。
            state.value = value
            this@collectWhenActive.collect { state.value = it }
        }
    }
    return state
}

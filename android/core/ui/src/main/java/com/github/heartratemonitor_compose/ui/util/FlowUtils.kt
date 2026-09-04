package com.github.heartratemonitor_compose.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// isActive 为 true 时收集 Flow，暂停保留初值；避免后台页面高频更新导致转场掉帧。
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

// StateFlow 专用重载：初值取 StateFlow.value 避免「空初始值→真实数据」1-2 帧跳变。
@Composable
fun <T> StateFlow<T>.collectWhenActive(
    isActive: Boolean
): State<T> {
    val state = remember { mutableStateOf(value) }
    LaunchedEffect(isActive) {
        if (isActive) {
            // ⚠️ 反直觉设计：isActive false→true 时先同步刷新到 StateFlow 当前值，
            // 避免预组合期 remember 读的 value 过时导致首帧显示过时数据。
            state.value = value
            this@collectWhenActive.collect { state.value = it }
        }
    }
    return state
}

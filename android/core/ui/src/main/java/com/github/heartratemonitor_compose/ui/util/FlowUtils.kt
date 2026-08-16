package com.github.heartratemonitor_compose.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow

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

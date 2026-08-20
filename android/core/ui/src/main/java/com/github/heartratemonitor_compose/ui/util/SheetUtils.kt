package com.github.heartratemonitor_compose.ui.util

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

/**
 * ModalBottomSheet 以 [SheetValue.Hidden] 初始化时不会自动播放展开动画，
 * 各页面此前均重复编写 `LaunchedEffect(Unit) { sheetState.expand() }`，
 * 本工具将该模式收敛为一处。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberExpandedSheetState(
    confirmValueChange: (SheetValue) -> Boolean = { true }
): SheetState {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        confirmValueChange = confirmValueChange
    )
    LaunchedEffect(Unit) { sheetState.expand() }
    return sheetState
}

/**
 * 为 BottomSheet 按钮提供带收起动画的 dismiss 回调。
 *
 * 直接在按钮 onClick 里把控制显隐的 state 置为 false 会令 ModalBottomSheet
 * 立即从 composition 移除，跳过 hide() 动画，导致弹窗突兀消失。
 *
 * 本函数返回一个 lambda，调用时先 [SheetState.hide] 播放下滑动画，
 * 动画完成后立即执行 [onDismiss] 完成业务清理（如置 false state），
 * 弹窗随即从 composition 移除——因此 ModalBottomSheet 的 onDismissRequest
 * 不会再被触发，不会产生重复回调。
 *
 * 对于 scrim 点击 / 系统返回键的关闭路径，ModalBottomSheet 内部自己调用
 * hide() 后会触发 onDismissRequest。因此 [onDismiss] 应与 onDismissRequest
 * 做相同的清理逻辑（通常是同一个 lambda），保证两条路径的清理一致。
 *
 * 用法：
 * ```
 * val dismiss = rememberSheetDismissHandler(sheetState) { showDialog = false }
 * ExpressiveButton(label = "确认", onClick = dismiss)
 * ```
 *
 * @param sheetState 当前 ModalBottomSheet 的 SheetState
 * @param onDismiss  动画完成后的业务回调（如置 state 为 false）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetDismissHandler(
    sheetState: SheetState,
    onDismiss: () -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    // 不将 onDismiss 作为 remember key：onDismiss 通常是捕获了可变 state 的 lambda，
    // 每次 recomposition 都是新实例，remember 会失效；且若 onDismiss 引用恰好稳定
    // 则会缓存住过期闭包。用 rememberUpdatedState 保证每次调用最新的 onDismiss。
    val latestOnDismiss = rememberUpdatedState(onDismiss)
    return remember(scope, sheetState) {
        {
            scope.launch {
                sheetState.hide()
                latestOnDismiss.value()
            }
        }
    }
}

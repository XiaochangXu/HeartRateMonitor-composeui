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

// ModalBottomSheet Hidden 初始不自动展开；收敛各页重复的 LaunchedEffect(Unit){ expand() }。
// enabledValues 排除 PartiallyExpanded 等价 skipPartiallyExpanded=true，返回手势因无半展开锚点直达 hide()。
// 仅限 ModalBottomSheet：Scaffold 把手分支会调 partialExpand() 抛异常。默认值须与 M3 rememberBottomSheetState 对齐。
// 两参数均为 rememberSaveable key，confirmValueChange 勿捕获重组可变状态（会重建 SheetState）。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberExpandedSheetState(
    enabledValues: Set<SheetValue> =
        setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
    confirmValueChange: (SheetValue) -> Boolean = { true }
): SheetState {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = enabledValues,
        confirmValueChange = confirmValueChange
    )
    LaunchedEffect(Unit) { sheetState.expand() }
    return sheetState
}

// 带动画的 dismiss 回调：先 hide() 播放下滑动画，再执行 onDismiss 清理。
// onDismiss 应与 onDismissRequest 逻辑一致（scrim/返回键走 hide()→onDismissRequest 路径）。
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

package com.github.heartratemonitor_compose.ui.util

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

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

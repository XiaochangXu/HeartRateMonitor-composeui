package com.github.heartratemonitor_compose.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI 基类：单一 UiState 下行 + 单一 dispatch 意图上行。
 *
 * 子类职责：定义 [I]（sealed Intent）与 [S]（不可变 UiState data class），
 * 在 [handleIntent] 中完成意图处理（含副作用调用），经 [setState] 归约状态。
 *
 * 持久化设置页注意：[uiState] 是 SettingsRepository 真源的派生投影，
 * 写路径必须 Intent → settings.set() → Flow 回流经 [setState] 刷新，
 * 禁止在归约内本地改写再写设置的"双写"。
 */
abstract class MviViewModel<S, I : Any>(initialState: S) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)

    val uiState: StateFlow<S> = _uiState.asStateFlow()

    // VM 内部读取，禁止 UI 层直接使用。
    protected val currentState: S get() = _uiState.value

    fun dispatch(intent: I) {
        viewModelScope.launch { handleIntent(intent) }
    }

    protected abstract suspend fun handleIntent(intent: I)

    // CAS 更新，保证多线程写安全。
    protected fun setState(reducer: (S) -> S) {
        _uiState.update(reducer)
    }
}

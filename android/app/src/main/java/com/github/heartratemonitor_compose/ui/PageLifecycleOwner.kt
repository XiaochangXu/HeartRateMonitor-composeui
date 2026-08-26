package com.github.heartratemonitor_compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * 页面级 [LifecycleOwner]：根据 [isActive] 控制每个 Pager 页面的生命周期状态。
 *
 * - 活跃页（[isActive] = true）：跟随父生命周期（通常 RESUMED）
 * - 非活跃页（[isActive] = false）：降至 STARTED——`collectAsStateWithLifecycle()`
 *   在 STARTED 以下自动暂停收集，后台页面不再因 Flow 发射而重组
 * - 父生命周期 DESTROYED/INITIALIZED/CREATED 时透传
 *
 * 用法：在 HorizontalPager 的 page lambda 中调用，用
 * `CompositionLocalProvider(LocalLifecycleOwner provides pageLifecycleOwner)` 包裹页面内容。
 */
@Composable
fun rememberPageLifecycleOwner(isActive: Boolean): LifecycleOwner {
    val parentLifecycle = LocalLifecycleOwner.current.lifecycle
    val currentActive by rememberUpdatedState(isActive)
    val owner = remember(parentLifecycle) { PageLifecycleOwner() }

    DisposableEffect(parentLifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            owner.update(parentLifecycle.currentState, currentActive)
        }
        parentLifecycle.addObserver(observer)
        owner.update(parentLifecycle.currentState, currentActive)
        onDispose {
            parentLifecycle.removeObserver(observer)
            owner.destroy()
        }
    }

    LaunchedEffect(isActive, parentLifecycle) {
        owner.update(parentLifecycle.currentState, isActive)
    }

    return owner
}

private class PageLifecycleOwner : LifecycleOwner {

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle = registry

    fun update(parentState: Lifecycle.State, isActive: Boolean) {
        registry.currentState = when {
            parentState == Lifecycle.State.DESTROYED -> Lifecycle.State.DESTROYED
            parentState == Lifecycle.State.INITIALIZED -> Lifecycle.State.INITIALIZED
            parentState == Lifecycle.State.CREATED -> Lifecycle.State.CREATED
            isActive -> parentState
            else -> Lifecycle.State.STARTED
        }
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

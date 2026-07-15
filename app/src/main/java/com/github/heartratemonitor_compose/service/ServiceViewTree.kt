package com.github.heartratemonitor_compose.service

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 为 Service 中托管的 [ComposeView][androidx.compose.ui.platform.ComposeView] 提供
 * [LifecycleOwner] + [SavedStateRegistryOwner] + [ViewModelStoreOwner]。
 *
 * Service 本身不是 LifecycleOwner，而 ComposeView 必须从 ViewTree 读取这三类 owner 才能驱动组合（composition）。
 * 本工具类在 [attachToView] 时将自身注入 ViewTree 并手工驱动生命周期：
 *
 * - ON_CREATE：[attachToView] 中 `performRestore(null)` 后立即派发
 * - ON_START / ON_RESUME：view attached to window 时派发
 * - ON_PAUSE / ON_STOP：view detached from window 时派发
 * - ON_DESTROY：由 Service.onDestroy 显式调用 [handleLifecycleEvent]
 *
 * 注意：SavedStateRegistry 契约要求 `performRestore` 必须在 Lifecycle 进入 STARTED 之前完成，
 * 故在 ON_CREATE 之前调用。此处不持久化任何状态（覆盖层无 UI 状态需要恢复）。
 */
class ServiceViewTreeOwners : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private var attached = false

    /**
     * 将本 owners 注入 [view] 的 ViewTree，并驱动 ON_CREATE。
     * 注册 attach/detach 监听以自动派发 START/RESUME/PAUSE/STOP。
     *
     * 必须在 [view] 被 [WindowManager.addView] 之前调用（本工具类监听 attach 事件派发 START/RESUME）；
     * 若调用时 view 已 attached，则立即派发 START/RESUME 兜底。
     */
    fun attachToView(view: View) {
        if (attached) return
        attached = true
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
            override fun onViewDetachedFromWindow(v: View) {
                if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
        })
        // 调用时已 attached 的兜底：监听器不会触发，需手工派发
        if (view.isAttachedToWindow) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    /**
     * 显式派发生命周期事件。Service.onDestroy 应调用
     * `handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)` 释放组合。
     */
    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}

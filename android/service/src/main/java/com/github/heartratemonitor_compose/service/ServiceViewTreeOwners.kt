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
 * Service 非 LifecycleOwner，ComposeView 却需从 ViewTree 读取三类 owner 才能驱动组合。
 *
 * ⚠️ 反直觉设计：SavedStateRegistry 契约要求 performRestore 必须在 STARTED 之前完成；
 * 手动驱动 ON_CREATE→attach→START/RESUME→detach→PAUSE/STOP→ON_DESTROY。
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
     * 注入 ViewTree 并驱动 ON_CREATE；注册 attach/detach 监听自动派发 START/RESUME/PAUSE/STOP。
     *
     * ⚠️ 反直觉设计：必须在 WindowManager.addView 之前调用；若调用时已 attached 则立即兜底派发。
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
        if (view.isAttachedToWindow) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    /**
     * 显式派发生命周期事件。ON_DESTORY 时额外 clear ViewModelStore，与 ComponentActivity 生命周期契约一致。
     */
    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
        if (event == Lifecycle.Event.ON_DESTROY) {
            viewModelStore.clear()
        }
    }
}

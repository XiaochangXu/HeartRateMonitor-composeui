package com.github.heartratemonitor_compose.data.settings.di

import javax.inject.Qualifier

/**
 * 应用级协程作用域 Qualifier（Phase 2 迁入）。
 *
 * 定义在 :data:settings（:app 与 :data:settings 均可引用，不破坏 :core:model 零依赖）。
 * 绑定由 :app 的 AppModule 提供（SupervisorJob + Dispatchers.Default，进程级单例）。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

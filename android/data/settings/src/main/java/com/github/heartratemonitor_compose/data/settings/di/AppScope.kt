package com.github.heartratemonitor_compose.data.settings.di

import javax.inject.Qualifier

// 应用级协程作用域 Qualifier（:app 与 :data:settings 均可引用，不破坏 :core:model 零依赖）。
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

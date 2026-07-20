package com.github.heartratemonitor_compose.data.di

import android.content.Context
import com.github.heartratemonitor_compose.HeartRateApp

/**
 * 通过 [Context] 获取 [AppContainer]。
 *
 * 适用于 Composable、ViewModel、Service 等持有上下文的位置，
 * 避免每个类单独维护单例构造逻辑。
 */
val Context.appContainer: AppContainer
    get() = (applicationContext as HeartRateApp).appContainer

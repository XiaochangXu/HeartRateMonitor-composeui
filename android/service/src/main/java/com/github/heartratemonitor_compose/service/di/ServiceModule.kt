package com.github.heartratemonitor_compose.service.di

import com.github.heartratemonitor_compose.service.ServiceController
import com.github.heartratemonitor_compose.service.ServiceLauncher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * [ServiceLauncher] 接口绑定 [ServiceController] 实现（契约 3 Service 抽象边界不变）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    abstract fun bindServiceLauncher(impl: ServiceController): ServiceLauncher
}

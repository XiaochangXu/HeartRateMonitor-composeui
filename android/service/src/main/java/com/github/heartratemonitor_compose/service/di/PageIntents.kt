package com.github.heartratemonitor_compose.service.di

import javax.inject.Qualifier

/** 通知直达报警页的 Intent 工厂绑定。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AlarmPageIntents

/** 通知直达 FairMemory 页的 Intent 工厂绑定。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FairMemoryPageIntents

package com.basira.os.core.windows.di

import com.basira.os.core.windows.SpatialWindowManager
import com.basira.os.core.windows.WebSpatialWindowManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WindowsModule {

    @Binds
    @Singleton
    abstract fun bindSpatialWindowManager(impl: WebSpatialWindowManager): SpatialWindowManager
}

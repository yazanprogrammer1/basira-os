package com.basira.os.core.render.di

import com.basira.os.core.render.Compositor
import com.basira.os.core.render.StubCompositor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RenderModule {

    @Binds
    @Singleton
    abstract fun bindCompositor(impl: StubCompositor): Compositor
}

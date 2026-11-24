package com.basira.os.core.spaces.di

import com.basira.os.core.spaces.SpacesRepository
import com.basira.os.core.spaces.StubSpacesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SpacesModule {

    @Binds
    @Singleton
    abstract fun bindSpacesRepository(impl: StubSpacesRepository): SpacesRepository
}

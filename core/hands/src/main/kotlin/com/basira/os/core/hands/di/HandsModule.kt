package com.basira.os.core.hands.di

import com.basira.os.core.hands.HandTracker
import com.basira.os.core.hands.StubHandTracker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HandsModule {

    @Binds
    @Singleton
    abstract fun bindHandTracker(impl: StubHandTracker): HandTracker
}

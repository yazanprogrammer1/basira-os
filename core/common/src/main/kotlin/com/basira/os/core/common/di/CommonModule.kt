package com.basira.os.core.common.di

import com.basira.os.core.common.coroutines.ApplicationScope
import com.basira.os.core.common.coroutines.DefaultDispatcherProvider
import com.basira.os.core.common.coroutines.DispatcherProvider
import com.basira.os.core.common.logging.AndroidLogger
import com.basira.os.core.common.logging.Logger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CommonModule {

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindLogger(impl: AndroidLogger): Logger

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(dispatchers: DispatcherProvider): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatchers.default)
    }
}

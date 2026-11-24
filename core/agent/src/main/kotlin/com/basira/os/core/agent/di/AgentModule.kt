package com.basira.os.core.agent.di

import com.basira.os.core.agent.LlmClient
import com.basira.os.core.agent.StubLlmClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AgentModule {

    @Binds
    @Singleton
    abstract fun bindLlmClient(impl: StubLlmClient): LlmClient
}

package com.basira.os.core.thermal.di

import com.basira.os.core.thermal.PowerManagerThermalStatusSource
import com.basira.os.core.thermal.StubThermalGovernor
import com.basira.os.core.thermal.ThermalGovernor
import com.basira.os.core.thermal.ThermalStatusSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ThermalModule {

    @Binds
    @Singleton
    abstract fun bindThermalGovernor(impl: StubThermalGovernor): ThermalGovernor

    @Binds
    @Singleton
    abstract fun bindThermalStatusSource(impl: PowerManagerThermalStatusSource): ThermalStatusSource
}

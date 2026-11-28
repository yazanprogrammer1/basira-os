package com.basira.os.core.render.di

import com.basira.os.core.common.panel.PanelHost
import com.basira.os.core.render.Compositor
import com.basira.os.core.render.StereoCompositor
import com.basira.os.core.render.calibration.CalibrationStore
import com.basira.os.core.render.calibration.DataStoreCalibrationStore
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
    abstract fun bindCompositor(impl: StereoCompositor): Compositor

    @Binds
    @Singleton
    abstract fun bindPanelHost(impl: StereoCompositor): PanelHost

    @Binds
    @Singleton
    abstract fun bindCalibrationStore(impl: DataStoreCalibrationStore): CalibrationStore
}

package com.basira.os.core.tracking.di

import com.basira.os.core.common.camera.CameraFrameSource
import com.basira.os.core.tracking.ArCoreApkSupport
import com.basira.os.core.tracking.ArCoreSupport
import com.basira.os.core.tracking.HeadTracker
import com.basira.os.core.tracking.StubHeadTracker
import com.basira.os.core.tracking.arcore.ArCoreCameraFrameSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TrackingModule {

    @Binds
    @Singleton
    abstract fun bindHeadTracker(impl: StubHeadTracker): HeadTracker

    @Binds
    @Singleton
    abstract fun bindArCoreSupport(impl: ArCoreApkSupport): ArCoreSupport

    @Binds
    @Singleton
    abstract fun bindCameraFrameSource(impl: ArCoreCameraFrameSource): CameraFrameSource
}

package com.basira.os.core.voice.di

import com.basira.os.core.voice.SpeechToText
import com.basira.os.core.voice.StubSpeechToText
import com.basira.os.core.voice.StubTextToSpeech
import com.basira.os.core.voice.StubWakeWordDetector
import com.basira.os.core.voice.TextToSpeech
import com.basira.os.core.voice.WakeWordDetector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindWakeWordDetector(impl: StubWakeWordDetector): WakeWordDetector

    @Binds
    @Singleton
    abstract fun bindSpeechToText(impl: StubSpeechToText): SpeechToText

    @Binds
    @Singleton
    abstract fun bindTextToSpeech(impl: StubTextToSpeech): TextToSpeech
}

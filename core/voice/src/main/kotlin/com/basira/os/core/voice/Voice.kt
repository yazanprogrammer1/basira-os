package com.basira.os.core.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

/** «يا بصيرة» / "Hey Basira". Phase 5 starts with push-to-talk, then an on-device KWS model. */
interface WakeWordDetector {
    val detections: Flow<Long>

    fun start()

    fun stop()
}

sealed interface SttResult {
    data class Partial(val text: String) : SttResult
    data class Final(val text: String, val confidence: Float?) : SttResult
    data class Error(val reason: String) : SttResult
}

interface SpeechToText {
    /** False when no provider or API key is configured. */
    val isConfigured: Boolean

    /** Listens once, emitting partial results and ending with Final or Error. */
    fun listen(languageTag: String = "ar"): Flow<SttResult>
}

interface TextToSpeech {
    suspend fun speak(text: String, languageTag: String = "ar")

    fun stop()
}

class StubWakeWordDetector @Inject constructor() : WakeWordDetector {
    override val detections: Flow<Long> = emptyFlow()
    override fun start() = Unit
    override fun stop() = Unit
}

class StubSpeechToText @Inject constructor() : SpeechToText {
    override val isConfigured: Boolean = BuildConfig.STT_API_KEY.isNotBlank()
    override fun listen(languageTag: String): Flow<SttResult> = emptyFlow()
}

class StubTextToSpeech @Inject constructor() : TextToSpeech {
    override suspend fun speak(text: String, languageTag: String) = Unit
    override fun stop() = Unit
}

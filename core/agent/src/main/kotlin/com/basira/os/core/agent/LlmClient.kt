package com.basira.os.core.agent

import javax.inject.Inject

/** Tools the assistant can call, see docs/ARCHITECTURE.md section 4. */
enum class AgentTool(val wireName: String, val changesState: Boolean) {
    OPEN_APP("open_app", changesState = false),
    CLOSE_WINDOW("close_window", changesState = false),
    CLOSE_ALL("close_all", changesState = false),
    PLACE_WINDOW("place_window", changesState = false),
    SEARCH("search", changesState = false),
    SUMMARIZE_PAGE("summarize_page", changesState = false),
    ASK_PAGE("ask_page", changesState = false),

    /** Fills fields only; submitting still needs a confirmation. */
    FILL_FORM("fill_form", changesState = true),
    CLICK_TEXT("click_text", changesState = true),
    RECALL_SPACE("recall_space", changesState = false),
    SAVE_SPACE("save_space", changesState = false),
}

data class LlmMessage(val role: Role, val content: String) {
    enum class Role { SYSTEM, USER, ASSISTANT, TOOL }
}

sealed interface LlmResponse {
    data class Text(val text: String) : LlmResponse
    data class ToolCall(val tool: AgentTool, val argumentsJson: String) : LlmResponse
    data object NotConfigured : LlmResponse
    data class Failure(val reason: String) : LlmResponse
}

/** Cloud now, on-device later. The key comes from local.properties via BuildConfig. */
interface LlmClient {
    val isConfigured: Boolean

    suspend fun complete(messages: List<LlmMessage>, tools: List<AgentTool>): LlmResponse
}

/** Phase 0 stub. Phase 5 adds a real provider behind this interface. */
class StubLlmClient @Inject constructor() : LlmClient {
    override val isConfigured: Boolean = BuildConfig.LLM_API_KEY.isNotBlank()

    override suspend fun complete(messages: List<LlmMessage>, tools: List<AgentTool>): LlmResponse =
        LlmResponse.NotConfigured
}

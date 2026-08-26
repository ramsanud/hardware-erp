package com.hardware.erp.ai;

import com.hardware.erp.ai.tool.AiTool;

import java.util.List;

/**
 * One LLM backend behind a stable interface, so a future second provider
 * (or a mock for tests) is a new implementation, not a rewrite of
 * AiChatService. AnthropicChatCompletionClient is the only implementation
 * today.
 */
public interface ChatCompletionClient {

    /** False when no API key is configured - AiChatService uses this to fail fast with a clear message instead of attempting a call that will only 401. */
    boolean isConfigured();

    /**
     * Runs the conversation to a final text answer, transparently handling
     * any tool-use round trips via {@code availableTools} along the way.
     * {@code history} is prior user/assistant turns (plain text only - no
     * tool-call detail is persisted or replayed across calls).
     */
    String chat(String systemPrompt, List<AiChatMessage> history, String userMessage, List<AiTool> availableTools);
}

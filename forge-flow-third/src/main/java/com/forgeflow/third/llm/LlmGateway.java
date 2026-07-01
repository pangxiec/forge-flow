package com.forgeflow.third.llm;

/**
 * Unified entry point for model calls.
 */
public interface LlmGateway {

    LlmChatResponse chat(LlmChatRequest request);
}

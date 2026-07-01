package com.forgeflow.third.llm;

import lombok.Builder;
import lombok.Data;

/**
 * Provider-neutral chat response returned by the LLM gateway.
 */
@Data
@Builder
public class LlmChatResponse {

    private String provider;

    private String model;

    private String content;

    private Long elapsedMillis;
}

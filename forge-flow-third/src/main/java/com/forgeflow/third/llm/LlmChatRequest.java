package com.forgeflow.third.llm;

import lombok.Builder;
import lombok.Data;

/**
 * Provider-neutral chat request used by ForgeFlow agents.
 */
@Data
@Builder
public class LlmChatRequest {

    private String scene;

    private String systemPrompt;

    private String userPrompt;

    private Integer timeoutSeconds;
}

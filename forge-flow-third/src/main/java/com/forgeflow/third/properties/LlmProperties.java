package com.forgeflow.third.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM configuration properties bound to {@code forge-flow.llm} prefix.
 */
@Data
@ConfigurationProperties(prefix = "forge-flow.llm")
public class LlmProperties {

    private String provider = "bailian";

    private String apiKey;

    private String model = "qwen-plus";

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private Integer timeoutSeconds = 180;

    private Integer maxRetries = 1;
}

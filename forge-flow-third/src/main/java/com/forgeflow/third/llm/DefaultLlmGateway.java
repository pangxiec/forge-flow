package com.forgeflow.third.llm;

import com.forgeflow.common.exception.BizException;
import com.forgeflow.third.client.BailianLlmClient;
import com.forgeflow.third.properties.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Routes agent requests to the configured LLM provider.
 */
public class DefaultLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultLlmGateway.class);

    private final LlmProperties llmProperties;
    private final BailianLlmClient bailianLlmClient;

    public DefaultLlmGateway(LlmProperties llmProperties, BailianLlmClient bailianLlmClient) {
        this.llmProperties = llmProperties;
        this.bailianLlmClient = bailianLlmClient;
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        validate(request);
        String provider = normalizeProvider(llmProperties.getProvider());
        long start = System.currentTimeMillis();
        int attempts = Math.max(1, resolveMaxRetries() + 1);
        BizException lastBizException = null;
        Exception lastException = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                if ("bailian".equals(provider)) {
                    LlmChatResponse response = bailianLlmClient.chat(request);
                    log.info("LLM gateway completed scene={}, provider={}, model={}, attempt={}, elapsed={}ms",
                            request.getScene(), response.getProvider(), response.getModel(), attempt, response.getElapsedMillis());
                    return response;
                }
                throw new BizException("Unsupported LLM provider: " + llmProperties.getProvider());
            } catch (BizException e) {
                lastBizException = e;
                log.warn("LLM gateway attempt failed scene={}, provider={}, attempt={}/{}, elapsed={}ms, message={}",
                        request.getScene(), provider, attempt, attempts, System.currentTimeMillis() - start, e.getMessage());
            } catch (Exception e) {
                lastException = e;
                log.warn("LLM gateway attempt failed scene={}, provider={}, attempt={}/{}, elapsed={}ms, message={}",
                        request.getScene(), provider, attempt, attempts, System.currentTimeMillis() - start, e.getMessage());
            }
        }

        if (lastBizException != null) {
            throw lastBizException;
        }
        throw new BizException("LLM gateway call failed: " + lastException.getMessage());
    }

    private void validate(LlmChatRequest request) {
        if (request == null) {
            throw new BizException("LLM request cannot be null");
        }
        if (!StringUtils.hasText(request.getSystemPrompt())) {
            throw new BizException("LLM system prompt cannot be blank");
        }
        if (!StringUtils.hasText(request.getUserPrompt())) {
            throw new BizException("LLM user prompt cannot be blank");
        }
    }

    private String normalizeProvider(String provider) {
        return StringUtils.hasText(provider) ? provider.trim().toLowerCase() : "bailian";
    }

    private int resolveMaxRetries() {
        Integer maxRetries = llmProperties.getMaxRetries();
        if (maxRetries == null || maxRetries < 0) {
            return 0;
        }
        return Math.min(maxRetries, 3);
    }
}

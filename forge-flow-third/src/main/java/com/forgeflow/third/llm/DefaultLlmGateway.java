package com.forgeflow.third.llm;

import com.forgeflow.common.exception.BizException;
import com.forgeflow.dao.domain.LlmCallLog;
import com.forgeflow.dao.mapper.LlmCallLogMapper;
import com.forgeflow.third.client.BailianLlmClient;
import com.forgeflow.third.properties.LlmProperties;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

/**
 * Routes agent requests to the configured LLM provider.
 */
public class DefaultLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultLlmGateway.class);

    private final LlmProperties llmProperties;
    private final BailianLlmClient bailianLlmClient;
    private final LlmCallLogMapper llmCallLogMapper;
    private final ThreadPoolTaskExecutor llmCallLogExecutor;

    public DefaultLlmGateway(
            LlmProperties llmProperties,
            BailianLlmClient bailianLlmClient,
            LlmCallLogMapper llmCallLogMapper,
            ThreadPoolTaskExecutor llmCallLogExecutor) {
        this.llmProperties = llmProperties;
        this.bailianLlmClient = bailianLlmClient;
        this.llmCallLogMapper = llmCallLogMapper;
        this.llmCallLogExecutor = llmCallLogExecutor;
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        validate(request);
        String provider = normalizeProvider(llmProperties.getProvider());
        LocalDateTime startedAt = LocalDateTime.now();
        long start = System.currentTimeMillis();
        int attempts = Math.max(1, resolveMaxRetries() + 1);
        int usedAttempts = 0;
        BizException lastBizException = null;
        Exception lastException = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            usedAttempts = attempt;
            try {
                if ("bailian".equals(provider)) {
                    LlmChatResponse response = bailianLlmClient.chat(request);
                    log.info("LLM gateway completed scene={}, provider={}, model={}, attempt={}, elapsed={}ms",
                            request.getScene(), response.getProvider(), response.getModel(), attempt, response.getElapsedMillis());
                    saveCallLog(request, response.getProvider(), response.getModel(), "SUCCESS",
                            response.getContent(), null, usedAttempts, startedAt, System.currentTimeMillis() - start);
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

        String errorMessage = lastBizException != null
                ? lastBizException.getMessage()
                : (lastException == null ? "unknown error" : lastException.getMessage());
        saveCallLog(request, provider, llmProperties.getModel(), "FAILED",
                null, errorMessage, usedAttempts, startedAt, System.currentTimeMillis() - start);

        if (lastBizException != null) {
            throw lastBizException;
        }
        throw new BizException("LLM gateway call failed: " + errorMessage);
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

    private void saveCallLog(
            LlmChatRequest request,
            String provider,
            String model,
            String status,
            String responseContent,
            String errorMessage,
            int attemptCount,
            LocalDateTime startedAt,
            long elapsedMillis) {
        try {
            llmCallLogExecutor.execute(() -> doSaveCallLog(
                    request,
                    provider,
                    model,
                    status,
                    responseContent,
                    errorMessage,
                    attemptCount,
                    startedAt,
                    elapsedMillis));
        } catch (Exception e) {
            log.warn("Failed to submit LLM call log scene={}, message={}", request.getScene(), e.getMessage());
        }
    }

    private void doSaveCallLog(
            LlmChatRequest request,
            String provider,
            String model,
            String status,
            String responseContent,
            String errorMessage,
            int attemptCount,
            LocalDateTime startedAt,
            long elapsedMillis) {
        try {
            LlmCallLog callLog = new LlmCallLog();
            callLog.setScene(defaultText(request.getScene(), "unknown"));
            callLog.setProvider(defaultText(provider, "unknown"));
            callLog.setModelName(model);
            callLog.setStatus(status);
            callLog.setProjectId(request.getProjectId());
            callLog.setBizType(request.getBizType());
            callLog.setBizId(request.getBizId());
            callLog.setPromptCharCount(charCount(request.getSystemPrompt()) + charCount(request.getUserPrompt()));
            callLog.setResponseCharCount(charCount(responseContent));
            callLog.setAttemptCount(attemptCount);
            callLog.setElapsedMillis(elapsedMillis);
            callLog.setErrorMessage(truncate(errorMessage, 2000));
            callLog.setStartedAt(startedAt);
            callLog.setFinishedAt(LocalDateTime.now());
            llmCallLogMapper.insert(callLog);
        } catch (Exception e) {
            log.warn("Failed to save LLM call log scene={}, message={}", request.getScene(), e.getMessage());
        }
    }

    private int charCount(String value) {
        return value == null ? 0 : value.length();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

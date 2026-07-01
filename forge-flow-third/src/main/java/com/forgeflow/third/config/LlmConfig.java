package com.forgeflow.third.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgeflow.dao.mapper.LlmCallLogMapper;
import com.forgeflow.third.client.BailianLlmClient;
import com.forgeflow.third.llm.DefaultLlmGateway;
import com.forgeflow.third.llm.LlmGateway;
import com.forgeflow.third.properties.LlmProperties;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

/**
 * Registers LLM-related beans for the Alibaba Bailian (DashScope) integration.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    @Bean
    public BailianLlmClient bailianLlmClient(LlmProperties llmProperties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(resolveTimeoutSeconds(llmProperties));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        RestClient restClient = RestClient.builder()
                .baseUrl(llmProperties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + llmProperties.getApiKey())
                .requestFactory(requestFactory)
                .build();
        return new BailianLlmClient(restClient, llmProperties, objectMapper);
    }

    @Bean
    public LlmGateway llmGateway(
            LlmProperties llmProperties,
            BailianLlmClient bailianLlmClient,
            LlmCallLogMapper llmCallLogMapper,
            ThreadPoolTaskExecutor llmCallLogExecutor) {
        return new DefaultLlmGateway(llmProperties, bailianLlmClient, llmCallLogMapper, llmCallLogExecutor);
    }

    @Bean
    public ThreadPoolTaskExecutor llmCallLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("llm-call-log-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }

    private long resolveTimeoutSeconds(LlmProperties llmProperties) {
        Integer timeoutSeconds = llmProperties.getTimeoutSeconds();
        if (timeoutSeconds == null || timeoutSeconds < 1) {
            return 180;
        }
        return timeoutSeconds;
    }
}

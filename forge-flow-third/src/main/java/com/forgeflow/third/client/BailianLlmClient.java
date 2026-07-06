package com.forgeflow.third.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgeflow.common.exception.BizException;
import com.forgeflow.third.llm.LlmChatRequest;
import com.forgeflow.third.llm.LlmChatResponse;
import com.forgeflow.third.properties.LlmProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Client for Alibaba Bailian (DashScope) LLM service, using the OpenAI-compatible API.
 */
public class BailianLlmClient {

    private final RestClient restClient;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;

    public BailianLlmClient(RestClient restClient, LlmProperties llmProperties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a chat completion request to the Bailian LLM and returns the assistant's reply.
     *
     * @param systemPrompt the system prompt that sets the assistant's role and output format
     * @param userPrompt   the user message containing the actual request content
     * @return the assistant's reply text
     */
    public String chat(String systemPrompt, String userPrompt) {
        return chat(LlmChatRequest.builder()
                .scene("legacy-chat")
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .build()).getContent();
    }

    public LlmChatResponse chat(LlmChatRequest request) {
        validateApiKey();

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        messages.add(Map.of("role", "user", "content", request.getUserPrompt()));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", llmProperties.getModel());
        requestBody.put("messages", messages);

        long start = System.currentTimeMillis();
        try {
            byte[] responseBytes = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(byte[].class);
            String responseJson = responseBytes == null ? "" : new String(responseBytes, StandardCharsets.UTF_8);

            return LlmChatResponse.builder()
                    .provider(llmProperties.getProvider())
                    .model(llmProperties.getModel())
                    .content(extractContent(responseJson))
                    .elapsedMillis(System.currentTimeMillis() - start)
                    .build();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("调用阿里百炼大模型失败: " + e.getMessage());
        }
    }

    private String extractContent(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode choices = root.path("choices");
            if (choices.isMissingNode() || !choices.isArray() || choices.isEmpty()) {
                throw new BizException("阿里百炼返回结果中没有 choices 字段");
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new BizException("阿里百炼返回结果内容为空");
            }
            return content;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("解析阿里百炼返回结果失败: " + e.getMessage());
        }
    }

    private void validateApiKey() {
        if (llmProperties.getApiKey() == null || llmProperties.getApiKey().isBlank()) {
            throw new BizException("阿里百炼 API Key 未配置，请检查 forge-flow.llm.api-key 配置项");
        }
    }
}

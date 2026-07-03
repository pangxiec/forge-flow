package com.forgeflow.admin.agent;

import lombok.Builder;

@Builder
public record PrdAgentStep(
        String name,
        String tool,
        String status,
        String summary,
        long elapsedMillis) {
}

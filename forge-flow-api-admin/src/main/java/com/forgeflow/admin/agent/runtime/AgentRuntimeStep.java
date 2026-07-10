package com.forgeflow.admin.agent.runtime;

import java.time.LocalDateTime;

public record AgentRuntimeStep(
        int order,
        String nodeName,
        String toolName,
        String status,
        String summary,
        long elapsedMillis,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {
}

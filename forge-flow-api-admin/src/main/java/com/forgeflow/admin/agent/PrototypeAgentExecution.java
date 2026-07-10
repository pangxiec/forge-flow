package com.forgeflow.admin.agent;

import java.util.List;
import lombok.Builder;

@Builder
public record PrototypeAgentExecution(
        String html,
        PrototypeAgentReview review,
        List<PrdAgentStep> steps,
        boolean clarificationRequired,
        List<String> clarificationQuestions,
        String memorySummary) {
}

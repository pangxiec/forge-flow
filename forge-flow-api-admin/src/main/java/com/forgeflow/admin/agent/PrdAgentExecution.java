package com.forgeflow.admin.agent;

import java.util.List;
import lombok.Builder;

@Builder
public record PrdAgentExecution(
        PrdAgent.RequirementAnalysis analysis,
        String prdMarkdown,
        PrdAgentReview review,
        List<PrdAgentStep> steps) {
}

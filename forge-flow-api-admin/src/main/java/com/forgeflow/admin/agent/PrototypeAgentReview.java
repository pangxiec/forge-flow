package com.forgeflow.admin.agent;

import java.util.List;
import lombok.Builder;

@Builder
public record PrototypeAgentReview(
        boolean passed,
        String summary,
        List<String> issues,
        List<String> suggestions) {
}

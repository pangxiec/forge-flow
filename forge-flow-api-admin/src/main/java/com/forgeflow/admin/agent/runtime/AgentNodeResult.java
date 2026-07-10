package com.forgeflow.admin.agent.runtime;

public record AgentNodeResult(Outcome outcome, String nextNode, String summary) {

    public enum Outcome { CONTINUE, WAITING_USER, COMPLETED }

    public static AgentNodeResult next(String nextNode, String summary) {
        return new AgentNodeResult(Outcome.CONTINUE, nextNode, summary);
    }

    public static AgentNodeResult waiting(String resumeNode, String summary) {
        return new AgentNodeResult(Outcome.WAITING_USER, resumeNode, summary);
    }

    public static AgentNodeResult completed(String summary) {
        return new AgentNodeResult(Outcome.COMPLETED, null, summary);
    }
}

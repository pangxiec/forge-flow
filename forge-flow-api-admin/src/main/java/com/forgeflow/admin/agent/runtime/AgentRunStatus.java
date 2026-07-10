package com.forgeflow.admin.agent.runtime;

public final class AgentRunStatus {
    public static final String READY = "READY";
    public static final String RUNNING = "RUNNING";
    public static final String WAITING_USER = "WAITING_USER";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    private AgentRunStatus() {
    }
}

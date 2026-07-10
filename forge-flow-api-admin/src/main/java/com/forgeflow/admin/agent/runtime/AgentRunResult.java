package com.forgeflow.admin.agent.runtime;

import java.util.List;

public record AgentRunResult<S>(
        S state,
        String status,
        String currentNode,
        String nextNode,
        List<AgentRuntimeStep> steps) {

    public boolean waitingUser() { return AgentRunStatus.WAITING_USER.equals(status); }
    public boolean completed() { return AgentRunStatus.COMPLETED.equals(status); }
}

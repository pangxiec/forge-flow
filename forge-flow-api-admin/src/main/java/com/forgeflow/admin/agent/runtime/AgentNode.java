package com.forgeflow.admin.agent.runtime;

@FunctionalInterface
public interface AgentNode<S> {
    AgentNodeResult execute(S state) throws Exception;
}

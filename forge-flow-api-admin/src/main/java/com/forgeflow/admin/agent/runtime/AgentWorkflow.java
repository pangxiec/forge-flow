package com.forgeflow.admin.agent.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentWorkflow<S> {

    private final String agentType;
    private final Class<S> stateType;
    private final String startNode;
    private final Map<String, NodeDefinition<S>> nodes;

    private AgentWorkflow(Builder<S> builder) {
        this.agentType = builder.agentType;
        this.stateType = builder.stateType;
        this.startNode = builder.startNode;
        this.nodes = Map.copyOf(builder.nodes);
    }

    public String agentType() { return agentType; }
    public Class<S> stateType() { return stateType; }
    public String startNode() { return startNode; }
    public NodeDefinition<S> node(String nodeId) { return nodes.get(nodeId); }

    public static <S> Builder<S> builder(String agentType, Class<S> stateType) {
        return new Builder<>(agentType, stateType);
    }

    public record NodeDefinition<S>(String id, String toolName, AgentNode<S> action) {
    }

    public static final class Builder<S> {
        private final String agentType;
        private final Class<S> stateType;
        private final Map<String, NodeDefinition<S>> nodes = new LinkedHashMap<>();
        private String startNode;

        private Builder(String agentType, Class<S> stateType) {
            this.agentType = agentType;
            this.stateType = stateType;
        }

        public Builder<S> startAt(String nodeId) {
            this.startNode = nodeId;
            return this;
        }

        public Builder<S> node(String id, String toolName, AgentNode<S> action) {
            nodes.put(id, new NodeDefinition<>(id, toolName, action));
            return this;
        }

        public AgentWorkflow<S> build() {
            if (agentType == null || agentType.isBlank()) {
                throw new IllegalArgumentException("agentType cannot be blank");
            }
            if (stateType == null) {
                throw new IllegalArgumentException("stateType cannot be null");
            }
            if (startNode == null || !nodes.containsKey(startNode)) {
                throw new IllegalArgumentException("startNode must reference a registered node");
            }
            return new AgentWorkflow<>(this);
        }
    }
}

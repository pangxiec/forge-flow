package com.forgeflow.admin.agent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgeflow.dao.domain.AgentCheckpoint;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AgentRuntime {

    private static final int MAX_NODE_EXECUTIONS = 50;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private AgentCheckpointRepository checkpointRepository;

    public <S> AgentRunResult<S> start(
            Long taskId,
            Long projectId,
            Long operatorId,
            AgentWorkflow<S> workflow,
            S initialState) {
        AgentCheckpoint checkpoint = newCheckpoint(taskId, projectId, operatorId, workflow, initialState);
        persist(checkpoint);
        return execute(operatorId, workflow, initialState, checkpoint);
    }

    public <S> AgentRunResult<S> resume(
            Long taskId,
            Long operatorId,
            AgentWorkflow<S> workflow,
            S refreshedState) {
        AgentCheckpoint checkpoint = checkpointRepository.findByTaskId(taskId)
                .orElseThrow(() -> new AgentRuntimeException("agent checkpoint not found for task " + taskId));
        boolean restartFromBeginning = AgentRunStatus.WAITING_USER.equals(checkpoint.getStatus());
        return resume(taskId, operatorId, workflow, refreshedState, restartFromBeginning);
    }

    public <S> AgentRunResult<S> resume(
            Long taskId,
            Long operatorId,
            AgentWorkflow<S> workflow,
            S refreshedState,
            boolean restartFromBeginning) {
        AgentCheckpoint checkpoint = checkpointRepository.findByTaskId(taskId)
                .orElseThrow(() -> new AgentRuntimeException("agent checkpoint not found for task " + taskId));
        if (!workflow.agentType().equals(checkpoint.getAgentType())) {
            throw new AgentRuntimeException("checkpoint agent type does not match workflow");
        }
        if (AgentRunStatus.COMPLETED.equals(checkpoint.getStatus())) {
            S completedState = deserialize(checkpoint.getStateJson(), workflow.stateType());
            return new AgentRunResult<>(completedState, checkpoint.getStatus(), checkpoint.getCurrentNode(),
                    checkpoint.getNextNode(), List.of());
        }

        S state;
        if (restartFromBeginning) {
            state = refreshedState;
            checkpoint.setCurrentNode(null);
            checkpoint.setNextNode(workflow.startNode());
        } else {
            state = deserialize(checkpoint.getStateJson(), workflow.stateType());
        }
        checkpoint.setStatus(AgentRunStatus.READY);
        checkpoint.setLastError(null);
        checkpoint.setStateJson(serialize(state));
        checkpoint.setUpdatedBy(operatorId);
        incrementVersion(checkpoint);
        persist(checkpoint);
        return execute(operatorId, workflow, state, checkpoint);
    }

    private <S> AgentRunResult<S> execute(
            Long operatorId,
            AgentWorkflow<S> workflow,
            S state,
            AgentCheckpoint checkpoint) {
        List<AgentRuntimeStep> executedSteps = new ArrayList<>();
        String nodeId = checkpoint.getNextNode();
        int executions = 0;
        while (nodeId != null) {
            if (++executions > MAX_NODE_EXECUTIONS) {
                throw failWithoutNode(checkpoint, "agent exceeded maximum node executions");
            }
            AgentWorkflow.NodeDefinition<S> definition = workflow.node(nodeId);
            if (definition == null) {
                throw failWithoutNode(checkpoint, "unknown agent node: " + nodeId);
            }

            checkpoint.setStatus(AgentRunStatus.RUNNING);
            checkpoint.setCurrentNode(nodeId);
            checkpoint.setNextNode(nodeId);
            checkpoint.setStateJson(serialize(state));
            checkpoint.setUpdatedBy(operatorId);
            incrementVersion(checkpoint);
            persist(checkpoint);

            LocalDateTime startedAt = LocalDateTime.now();
            long startedNanos = System.nanoTime();
            String stableStateJson = checkpoint.getStateJson();
            try {
                AgentNodeResult nodeResult = definition.action().execute(state);
                LocalDateTime finishedAt = LocalDateTime.now();
                long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000;
                String stepStatus = nodeResult.outcome() == AgentNodeResult.Outcome.WAITING_USER
                        ? AgentRunStatus.WAITING_USER
                        : "SUCCESS";
                AgentRuntimeStep step = new AgentRuntimeStep(
                        nextStepSequence(checkpoint), nodeId, definition.toolName(), stepStatus,
                        nodeResult.summary(), elapsedMillis, startedAt, finishedAt);

                checkpoint.setStateJson(serialize(state));
                checkpoint.setLastError(null);
                checkpoint.setNextNode(nodeResult.nextNode());
                if (nodeResult.outcome() == AgentNodeResult.Outcome.WAITING_USER) {
                    checkpoint.setStatus(AgentRunStatus.WAITING_USER);
                } else if (nodeResult.outcome() == AgentNodeResult.Outcome.COMPLETED) {
                    checkpoint.setStatus(AgentRunStatus.COMPLETED);
                    checkpoint.setNextNode(null);
                } else {
                    checkpoint.setStatus(AgentRunStatus.READY);
                }
                checkpoint.setUpdatedBy(operatorId);
                incrementVersion(checkpoint);
                persistWithStep(checkpoint, step, operatorId);
                executedSteps.add(step);

                if (!AgentRunStatus.READY.equals(checkpoint.getStatus())) {
                    return new AgentRunResult<>(state, checkpoint.getStatus(), checkpoint.getCurrentNode(),
                            checkpoint.getNextNode(), List.copyOf(executedSteps));
                }
                nodeId = checkpoint.getNextNode();
            } catch (Exception e) {
                LocalDateTime finishedAt = LocalDateTime.now();
                long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000;
                checkpoint.setStatus(AgentRunStatus.FAILED);
                checkpoint.setNextNode(nodeId);
                checkpoint.setStateJson(stableStateJson);
                checkpoint.setLastError(messageOf(e));
                checkpoint.setUpdatedBy(operatorId);
                AgentRuntimeStep failedStep = new AgentRuntimeStep(
                        nextStepSequence(checkpoint), nodeId, definition.toolName(), AgentRunStatus.FAILED,
                        messageOf(e), elapsedMillis, startedAt, finishedAt);
                incrementVersion(checkpoint);
                persistWithStep(checkpoint, failedStep, operatorId);
                throw new AgentRuntimeException("agent node failed: " + nodeId + ": " + messageOf(e), e);
            }
        }
        checkpoint.setStatus(AgentRunStatus.COMPLETED);
        checkpoint.setStateJson(serialize(state));
        checkpoint.setUpdatedBy(operatorId);
        incrementVersion(checkpoint);
        persist(checkpoint);
        return new AgentRunResult<>(state, checkpoint.getStatus(), checkpoint.getCurrentNode(), null,
                List.copyOf(executedSteps));
    }

    private <S> AgentCheckpoint newCheckpoint(
            Long taskId,
            Long projectId,
            Long operatorId,
            AgentWorkflow<S> workflow,
            S initialState) {
        AgentCheckpoint checkpoint = new AgentCheckpoint();
        checkpoint.setTaskId(taskId);
        checkpoint.setProjectId(projectId);
        checkpoint.setAgentType(workflow.agentType());
        checkpoint.setStatus(AgentRunStatus.READY);
        checkpoint.setNextNode(workflow.startNode());
        checkpoint.setStateJson(serialize(initialState));
        checkpoint.setStepSequence(0);
        checkpoint.setCheckpointVersion(1);
        checkpoint.setCreatedBy(operatorId);
        checkpoint.setUpdatedBy(operatorId);
        return checkpoint;
    }

    private int nextStepSequence(AgentCheckpoint checkpoint) {
        int next = checkpoint.getStepSequence() == null ? 1 : checkpoint.getStepSequence() + 1;
        checkpoint.setStepSequence(next);
        return next;
    }

    private void incrementVersion(AgentCheckpoint checkpoint) {
        int next = checkpoint.getCheckpointVersion() == null ? 1 : checkpoint.getCheckpointVersion() + 1;
        checkpoint.setCheckpointVersion(next);
    }

    private String serialize(Object state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new AgentRuntimeException("failed to serialize agent state", e);
        }
    }

    private <S> S deserialize(String stateJson, Class<S> stateType) {
        try {
            return objectMapper.readValue(stateJson, stateType);
        } catch (JsonProcessingException e) {
            throw new AgentRuntimeException("failed to restore agent state", e);
        }
    }

    private AgentRuntimeException failWithoutNode(AgentCheckpoint checkpoint, String message) {
        checkpoint.setStatus(AgentRunStatus.FAILED);
        checkpoint.setLastError(message);
        incrementVersion(checkpoint);
        persist(checkpoint);
        return new AgentRuntimeException(message);
    }

    private String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void persist(AgentCheckpoint checkpoint) {
        if (checkpoint.getTaskId() != null) {
            checkpointRepository.save(checkpoint);
        }
    }

    private void persistWithStep(AgentCheckpoint checkpoint, AgentRuntimeStep step, Long operatorId) {
        if (checkpoint.getTaskId() != null) {
            checkpointRepository.saveWithStep(checkpoint, step, operatorId);
        }
    }
}

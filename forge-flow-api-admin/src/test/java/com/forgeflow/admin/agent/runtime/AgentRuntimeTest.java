package com.forgeflow.admin.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgeflow.dao.domain.AgentCheckpoint;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentRuntimeTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private AgentCheckpointRepository checkpointRepository;

    @InjectMocks
    private AgentRuntime runtime;

    @Test
    void routesDynamicallyUntilPlannerCompletes() {
        AgentWorkflow<CounterState> workflow = counterWorkflow();

        AgentRunResult<CounterState> result = runtime.start(10L, 20L, 30L, workflow, new CounterState());

        assertThat(result.completed()).isTrue();
        assertThat(result.state().count).isEqualTo(3);
        assertThat(result.steps()).extracting(AgentRuntimeStep::nodeName)
                .containsExactly("plan", "increment", "plan", "increment", "plan", "increment", "plan", "finish");
    }

    @Test
    void persistsWaitingStateAndResumeNode() {
        AgentWorkflow<CounterState> workflow = AgentWorkflow.builder("WAITING_TEST", CounterState.class)
                .startAt("ask-user")
                .node("ask-user", "QuestionTool", state -> {
                    state.count = 1;
                    return AgentNodeResult.waiting("continue", "need user input");
                })
                .node("continue", "ContinueTool", state -> AgentNodeResult.completed("done"))
                .build();

        AgentRunResult<CounterState> result = runtime.start(11L, 20L, 30L, workflow, new CounterState());

        assertThat(result.waitingUser()).isTrue();
        assertThat(result.nextNode()).isEqualTo("continue");
        assertThat(result.state().count).isEqualTo(1);
    }

    @Test
    void resumesFromDurableNextNode() throws Exception {
        AgentWorkflow<CounterState> workflow = AgentWorkflow.builder("RESUME_TEST", CounterState.class)
                .startAt("increment")
                .node("increment", "CounterTool", state -> {
                    state.count++;
                    return AgentNodeResult.completed("done");
                })
                .build();
        CounterState savedState = new CounterState();
        savedState.count = 4;
        AgentCheckpoint checkpoint = new AgentCheckpoint();
        checkpoint.setTaskId(12L);
        checkpoint.setProjectId(20L);
        checkpoint.setAgentType("RESUME_TEST");
        checkpoint.setStatus(AgentRunStatus.FAILED);
        checkpoint.setNextNode("increment");
        checkpoint.setStateJson(objectMapper.writeValueAsString(savedState));
        checkpoint.setStepSequence(2);
        checkpoint.setCheckpointVersion(3);
        when(checkpointRepository.findByTaskId(anyLong())).thenReturn(Optional.of(checkpoint));

        AgentRunResult<CounterState> result = runtime.resume(
                12L, 30L, workflow, new CounterState());

        assertThat(result.completed()).isTrue();
        assertThat(result.state().count).isEqualTo(5);
    }

    @Test
    void waitingResumeRestartsPlanningWithRefreshedBusinessInput() throws Exception {
        AgentWorkflow<CounterState> workflow = AgentWorkflow.builder("WAITING_RESUME_TEST", CounterState.class)
                .startAt("replan")
                .node("replan", "Planner", state -> {
                    state.count++;
                    return AgentNodeResult.completed("done");
                })
                .build();
        CounterState staleState = new CounterState();
        staleState.count = 1;
        AgentCheckpoint checkpoint = new AgentCheckpoint();
        checkpoint.setTaskId(13L);
        checkpoint.setProjectId(20L);
        checkpoint.setAgentType("WAITING_RESUME_TEST");
        checkpoint.setStatus(AgentRunStatus.WAITING_USER);
        checkpoint.setNextNode("replan");
        checkpoint.setStateJson(objectMapper.writeValueAsString(staleState));
        checkpoint.setStepSequence(1);
        checkpoint.setCheckpointVersion(2);
        when(checkpointRepository.findByTaskId(anyLong())).thenReturn(Optional.of(checkpoint));
        CounterState refreshedState = new CounterState();
        refreshedState.count = 10;

        AgentRunResult<CounterState> result = runtime.resume(
                13L, 30L, workflow, refreshedState);

        assertThat(result.completed()).isTrue();
        assertThat(result.state().count).isEqualTo(11);
    }

    private AgentWorkflow<CounterState> counterWorkflow() {
        return AgentWorkflow.builder("COUNTER_TEST", CounterState.class)
                .startAt("plan")
                .node("plan", "Planner", state -> AgentNodeResult.next(
                        state.count < 3 ? "increment" : "finish", "count=" + state.count))
                .node("increment", "CounterTool", state -> {
                    state.count++;
                    return AgentNodeResult.next("plan", "count=" + state.count);
                })
                .node("finish", "FinishTool", state -> AgentNodeResult.completed("done"))
                .build();
    }

    static class CounterState {
        public int count;
    }
}

package com.forgeflow.admin.agent.runtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forgeflow.dao.domain.AgentCheckpoint;
import com.forgeflow.dao.domain.GenerationTaskStep;
import com.forgeflow.dao.mapper.AgentCheckpointMapper;
import com.forgeflow.dao.mapper.GenerationTaskStepMapper;
import jakarta.annotation.Resource;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AgentCheckpointRepository {

    @Resource
    private AgentCheckpointMapper checkpointMapper;

    @Resource
    private GenerationTaskStepMapper stepMapper;

    public Optional<AgentCheckpoint> findByTaskId(Long taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(checkpointMapper.selectOne(Wrappers.<AgentCheckpoint>lambdaQuery()
                .eq(AgentCheckpoint::getTaskId, taskId)
                .last("LIMIT 1")));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AgentCheckpoint checkpoint) {
        upsert(checkpoint);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveWithStep(AgentCheckpoint checkpoint, AgentRuntimeStep runtimeStep, Long operatorId) {
        GenerationTaskStep step = new GenerationTaskStep();
        step.setTaskId(checkpoint.getTaskId());
        step.setProjectId(checkpoint.getProjectId());
        step.setStepOrder(runtimeStep.order());
        step.setStepName(runtimeStep.nodeName());
        step.setToolName(runtimeStep.toolName());
        step.setStatus(runtimeStep.status());
        step.setSummary(truncate(runtimeStep.summary(), 6000));
        step.setElapsedMillis(runtimeStep.elapsedMillis());
        step.setStartedAt(runtimeStep.startedAt());
        step.setFinishedAt(runtimeStep.finishedAt());
        step.setCreatedBy(operatorId);
        step.setUpdatedBy(operatorId);
        stepMapper.insert(step);
        upsert(checkpoint);
    }

    private void upsert(AgentCheckpoint checkpoint) {
        AgentCheckpoint existing = checkpointMapper.selectOne(Wrappers.<AgentCheckpoint>lambdaQuery()
                .eq(AgentCheckpoint::getTaskId, checkpoint.getTaskId())
                .last("LIMIT 1"));
        if (existing == null) {
            checkpointMapper.insert(checkpoint);
            return;
        }
        checkpoint.setId(existing.getId());
        checkpoint.setCreatedAt(existing.getCreatedAt());
        checkpoint.setCreatedBy(existing.getCreatedBy());
        checkpoint.setVersion(existing.getVersion());
        checkpointMapper.updateById(checkpoint);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

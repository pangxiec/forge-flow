package com.forgeflow.admin.service;

import com.forgeflow.common.exception.BizException;
import com.forgeflow.dao.domain.AgentCheckpoint;
import com.forgeflow.dao.domain.GenerationTask;
import com.forgeflow.dao.mapper.AgentCheckpointMapper;
import com.forgeflow.dao.mapper.GenerationTaskMapper;
import com.forgeflow.pojo.vo.resp.RespAgentRuntimeVo;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
public class GenerationTaskRuntimeService {

    @Resource
    private GenerationTaskMapper generationTaskMapper;

    @Resource
    private AgentCheckpointMapper checkpointMapper;

    @Resource
    private PrdService prdService;

    @Resource
    private PrototypeService prototypeService;

    public Object resume(Long taskId) {
        GenerationTask task = generationTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("generation task not found");
        }
        return switch (task.getTaskType()) {
            case "PRD" -> prdService.resume(taskId);
            case "PROTOTYPE" -> prototypeService.resume(taskId);
            default -> throw new BizException("task type does not support agent resume: " + task.getTaskType());
        };
    }

    public RespAgentRuntimeVo getRuntime(Long taskId) {
        AgentCheckpoint checkpoint = checkpointMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<AgentCheckpoint>lambdaQuery()
                        .eq(AgentCheckpoint::getTaskId, taskId)
                        .last("LIMIT 1"));
        if (checkpoint == null) {
            throw new BizException("agent checkpoint not found");
        }
        RespAgentRuntimeVo response = new RespAgentRuntimeVo();
        BeanUtils.copyProperties(checkpoint, response);
        return response;
    }
}

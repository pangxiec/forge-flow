package com.forgeflow.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forgeflow.admin.agent.PrdAgentStep;
import com.forgeflow.admin.service.GenerationTaskStepService;
import com.forgeflow.dao.domain.GenerationTaskStep;
import com.forgeflow.dao.mapper.GenerationTaskStepMapper;
import com.forgeflow.pojo.vo.resp.RespGenerationTaskStepVo;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GenerationTaskStepServiceImpl implements GenerationTaskStepService {

    @Resource
    private GenerationTaskStepMapper generationTaskStepMapper;

    @Override
    public void savePrdAgentSteps(Long taskId, Long projectId, Long operatorId, List<PrdAgentStep> steps) {
        if (taskId == null || projectId == null || steps == null || steps.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < steps.size(); index++) {
            PrdAgentStep source = steps.get(index);
            GenerationTaskStep step = new GenerationTaskStep();
            step.setTaskId(taskId);
            step.setProjectId(projectId);
            step.setStepOrder(index + 1);
            step.setStepName(source.name());
            step.setToolName(source.tool());
            step.setStatus(source.status());
            step.setSummary(source.summary());
            step.setElapsedMillis(source.elapsedMillis());
            step.setStartedAt(now.minusNanos(source.elapsedMillis() * 1_000_000));
            step.setFinishedAt(now);
            step.setCreatedBy(operatorId);
            step.setUpdatedBy(operatorId);
            generationTaskStepMapper.insert(step);
        }
    }

    @Override
    public List<RespGenerationTaskStepVo> listByTaskId(Long taskId) {
        return generationTaskStepMapper.selectList(Wrappers.<GenerationTaskStep>lambdaQuery()
                        .eq(GenerationTaskStep::getTaskId, taskId)
                        .orderByAsc(GenerationTaskStep::getStepOrder))
                .stream()
                .map(step -> BeanUtil.copyProperties(step, RespGenerationTaskStepVo.class))
                .toList();
    }
}

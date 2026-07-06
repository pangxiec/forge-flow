package com.forgeflow.admin.service;

import com.forgeflow.admin.agent.PrdAgentStep;
import com.forgeflow.pojo.vo.resp.RespGenerationTaskStepVo;
import java.util.List;

public interface GenerationTaskStepService {

    void savePrdAgentSteps(Long taskId, Long projectId, Long operatorId, List<PrdAgentStep> steps);

    List<RespGenerationTaskStepVo> listByTaskId(Long taskId);
}

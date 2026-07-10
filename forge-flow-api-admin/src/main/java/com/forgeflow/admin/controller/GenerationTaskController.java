package com.forgeflow.admin.controller;

import com.forgeflow.admin.service.GenerationTaskStepService;
import com.forgeflow.admin.service.GenerationTaskRuntimeService;
import com.forgeflow.common.annotation.EnableResponseResult;
import com.forgeflow.pojo.vo.resp.RespAgentRuntimeVo;
import com.forgeflow.pojo.vo.resp.RespGenerationTaskStepVo;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@EnableResponseResult
@RestController
@RequestMapping("/api/v1/generation-task")
public class GenerationTaskController {

    @Resource
    private GenerationTaskStepService generationTaskStepService;

    @Resource
    private GenerationTaskRuntimeService generationTaskRuntimeService;

    @GetMapping("/{taskId}/steps")
    public List<RespGenerationTaskStepVo> listSteps(@PathVariable("taskId") Long taskId) {
        return generationTaskStepService.listByTaskId(taskId);
    }

    @GetMapping("/{taskId}/runtime")
    public RespAgentRuntimeVo getRuntime(@PathVariable("taskId") Long taskId) {
        return generationTaskRuntimeService.getRuntime(taskId);
    }

    @PostMapping("/{taskId}/resume")
    public Object resume(@PathVariable("taskId") Long taskId) {
        return generationTaskRuntimeService.resume(taskId);
    }
}

package com.forgeflow.admin.controller;

import com.forgeflow.admin.service.ProjectService;
import com.forgeflow.common.annotation.EnableResponseResult;
import com.forgeflow.pojo.vo.req.ReqCreateProjectVo;
import com.forgeflow.pojo.vo.resp.RespProjectVo;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@EnableResponseResult
@RestController
@RequestMapping("/api/v1/project")
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @PostMapping("/create")
    public RespProjectVo createProject(@RequestBody @Valid ReqCreateProjectVo reqVo) {
        return projectService.createProject(reqVo);
    }

    @GetMapping("/detail/{id}")
    public RespProjectVo getProject(@PathVariable("id") Long id) {
        return projectService.getProject(id);
    }

    @GetMapping("/list")
    public List<RespProjectVo> listProjects() {
        return projectService.listProjects();
    }
}

package com.forgeflow.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forgeflow.admin.service.AuditLogService;
import com.forgeflow.admin.service.ProjectService;
import com.forgeflow.common.enums.ProjectStatusEnum;
import com.forgeflow.common.enums.UserRoleEnum;
import com.forgeflow.common.exception.BizException;
import com.forgeflow.dao.domain.Project;
import com.forgeflow.dao.mapper.ProjectMapper;
import com.forgeflow.pojo.vo.req.ReqCreateProjectVo;
import com.forgeflow.pojo.vo.resp.RespProjectVo;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectServiceImpl implements ProjectService {

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private AuditLogService auditLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RespProjectVo createProject(ReqCreateProjectVo reqVo) {
        Long count = projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
                .eq(Project::getProjectCode, reqVo.getProjectCode()));
        if (count > 0) {
            throw new BizException("projectCode already exists");
        }

        Project project = new Project();
        project.setProjectName(reqVo.getProjectName());
        project.setProjectCode(reqVo.getProjectCode());
        project.setDescription(reqVo.getDescription());
        project.setManagerId(reqVo.getManagerId());
        project.setCurrentStage(ProjectStatusEnum.DRAFT.getCode());
        project.setStatus(ProjectStatusEnum.DRAFT.getCode());
        project.setCreatedBy(reqVo.getManagerId());
        project.setUpdatedBy(reqVo.getManagerId());
        projectMapper.insert(project);

        auditLogService.record(reqVo.getManagerId(), UserRoleEnum.PROJECT_MANAGER.getCode(), "CREATE_PROJECT",
                "PROJECT", project.getId(), null, project.getProjectCode());
        return convert(project);
    }

    @Override
    public RespProjectVo getProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BizException("project not found");
        }
        return convert(project);
    }

    @Override
    public List<RespProjectVo> listProjects() {
        return projectMapper.selectList(Wrappers.<Project>lambdaQuery()
                        .orderByDesc(Project::getUpdatedAt))
                .stream()
                .map(this::convert)
                .toList();
    }

    private RespProjectVo convert(Project project) {
        RespProjectVo respVo = new RespProjectVo();
        BeanUtils.copyProperties(project, respVo);
        return respVo;
    }
}

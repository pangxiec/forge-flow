package com.forgeflow.admin.service;

import com.forgeflow.pojo.vo.req.ReqCreateProjectVo;
import com.forgeflow.pojo.vo.resp.RespProjectVo;
import java.util.List;

public interface ProjectService {

    RespProjectVo createProject(ReqCreateProjectVo reqVo);

    RespProjectVo getProject(Long id);

    List<RespProjectVo> listProjects();
}

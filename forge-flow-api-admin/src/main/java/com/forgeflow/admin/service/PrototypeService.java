package com.forgeflow.admin.service;

import com.forgeflow.pojo.vo.req.ReqGeneratePrototypeVo;
import com.forgeflow.pojo.vo.resp.RespPrototypeArtifactVo;

public interface PrototypeService {

    RespPrototypeArtifactVo generate(ReqGeneratePrototypeVo reqVo);

    RespPrototypeArtifactVo getLatest(Long projectId);
}

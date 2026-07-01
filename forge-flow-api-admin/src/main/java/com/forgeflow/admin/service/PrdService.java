package com.forgeflow.admin.service;

import com.forgeflow.pojo.vo.req.ReqGeneratePrdVo;
import com.forgeflow.pojo.vo.resp.RespPrdDocumentVo;

public interface PrdService {

    RespPrdDocumentVo generate(ReqGeneratePrdVo reqVo);

    RespPrdDocumentVo getLatest(Long projectId);
}

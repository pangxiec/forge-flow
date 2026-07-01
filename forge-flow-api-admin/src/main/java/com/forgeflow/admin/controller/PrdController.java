package com.forgeflow.admin.controller;

import com.forgeflow.admin.service.PrdService;
import com.forgeflow.common.annotation.EnableResponseResult;
import com.forgeflow.pojo.vo.req.ReqConfirmPrdVo;
import com.forgeflow.pojo.vo.req.ReqGeneratePrdVo;
import com.forgeflow.pojo.vo.resp.RespPrdDocumentVo;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@EnableResponseResult
@RestController
@RequestMapping("/api/v1/prd")
public class PrdController {

    @Resource
    private PrdService prdService;

    @PostMapping("/generate")
    public RespPrdDocumentVo generate(@RequestBody @Valid ReqGeneratePrdVo reqVo) {
        return prdService.generate(reqVo);
    }

    @PostMapping("/confirm")
    public RespPrdDocumentVo confirm(@RequestBody @Valid ReqConfirmPrdVo reqVo) {
        return prdService.confirm(reqVo);
    }

    @GetMapping("/latest/{projectId}")
    public RespPrdDocumentVo getLatest(@PathVariable("projectId") Long projectId) {
        return prdService.getLatest(projectId);
    }
}

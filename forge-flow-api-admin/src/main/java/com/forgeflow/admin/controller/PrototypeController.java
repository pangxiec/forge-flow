package com.forgeflow.admin.controller;

import com.forgeflow.admin.service.PrototypeService;
import com.forgeflow.common.annotation.EnableResponseResult;
import com.forgeflow.pojo.vo.req.ReqGeneratePrototypeVo;
import com.forgeflow.pojo.vo.resp.RespPrototypeArtifactVo;
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
@RequestMapping("/api/v1/prototype")
public class PrototypeController {

    @Resource
    private PrototypeService prototypeService;

    @PostMapping("/generate")
    public RespPrototypeArtifactVo generate(@RequestBody @Valid ReqGeneratePrototypeVo reqVo) {
        return prototypeService.generate(reqVo);
    }

    @GetMapping("/latest/{projectId}")
    public RespPrototypeArtifactVo getLatest(@PathVariable("projectId") Long projectId) {
        return prototypeService.getLatest(projectId);
    }
}

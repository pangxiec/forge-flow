package com.forgeflow.admin.controller;

import com.forgeflow.admin.service.RequirementService;
import com.forgeflow.common.annotation.EnableResponseResult;
import com.forgeflow.pojo.vo.req.ReqAnalyzeRequirementVo;
import com.forgeflow.pojo.vo.resp.RespRequirementAnalysisVo;
import com.forgeflow.pojo.vo.resp.RespRequirementDetailVo;
import com.forgeflow.pojo.vo.resp.RespRequirementUploadVo;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@EnableResponseResult
@RestController
@RequestMapping("/api/v1/requirement")
public class RequirementController {

    @Resource
    private RequirementService requirementService;

    @PostMapping("/upload")
    public RespRequirementUploadVo upload(@RequestParam("projectId") Long projectId,
            @RequestParam("title") String title,
            @RequestParam("sourceType") String sourceType,
            @RequestParam("priority") String priority,
            @RequestParam("requester") String requester,
            @RequestParam("productOwner") String productOwner,
            @RequestParam(value = "expectedDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDate,
            @RequestParam("background") String background,
            @RequestParam("objective") String objective,
            @RequestParam("scope") String scope,
            @RequestParam("sensitiveMasked") Boolean sensitiveMasked,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return requirementService.upload(projectId, title, sourceType, priority, requester, productOwner, expectedDate,
                background, objective, scope, sensitiveMasked, files);
    }

    @PostMapping("/analyze")
    public RespRequirementAnalysisVo analyze(@RequestBody @Valid ReqAnalyzeRequirementVo reqVo) {
        return requirementService.analyze(reqVo);
    }

    @GetMapping("/latest-analysis/{projectId}")
    public RespRequirementAnalysisVo getLatestAnalysis(@PathVariable("projectId") Long projectId) {
        return requirementService.getLatestAnalysis(projectId);
    }

    @GetMapping("/latest/{projectId}")
    public RespRequirementDetailVo getLatest(@PathVariable("projectId") Long projectId) {
        return requirementService.getLatest(projectId);
    }

    @GetMapping("/{projectId}/{requirementId}")
    public RespRequirementDetailVo getDetail(
            @PathVariable("projectId") Long projectId,
            @PathVariable("requirementId") Long requirementId) {
        return requirementService.getDetail(projectId, requirementId);
    }
}

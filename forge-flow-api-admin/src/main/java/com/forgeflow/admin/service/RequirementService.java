package com.forgeflow.admin.service;

import com.forgeflow.pojo.vo.req.ReqAnalyzeRequirementVo;
import com.forgeflow.pojo.vo.resp.RespRequirementAnalysisVo;
import com.forgeflow.pojo.vo.resp.RespRequirementUploadVo;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface RequirementService {

    RespRequirementUploadVo upload(Long projectId, String title, String sourceType, String priority, String requester,
            String productOwner, LocalDate expectedDate, String background, String objective, String scope,
            Boolean sensitiveMasked, List<MultipartFile> files);

    RespRequirementAnalysisVo analyze(ReqAnalyzeRequirementVo reqVo);

    RespRequirementAnalysisVo getLatestAnalysis(Long projectId);
}

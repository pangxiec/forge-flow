package com.forgeflow.admin.service.impl;

import com.forgeflow.admin.service.AuditLogService;
import com.forgeflow.dao.domain.AuditLog;
import com.forgeflow.dao.mapper.AuditLogMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    @Resource
    private AuditLogMapper auditLogMapper;

    @Override
    public void record(Long userId, String userRole, String operationType, String targetType, Long targetId,
            String beforeContent, String afterContent) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setUserRole(userRole);
        auditLog.setOperationType(operationType);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setBeforeContent(beforeContent);
        auditLog.setAfterContent(afterContent);
        auditLogMapper.insert(auditLog);
    }
}

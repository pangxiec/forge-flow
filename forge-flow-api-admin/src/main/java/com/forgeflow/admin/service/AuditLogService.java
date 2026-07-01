package com.forgeflow.admin.service;

public interface AuditLogService {

    void record(Long userId, String userRole, String operationType, String targetType, Long targetId,
            String beforeContent, String afterContent);
}

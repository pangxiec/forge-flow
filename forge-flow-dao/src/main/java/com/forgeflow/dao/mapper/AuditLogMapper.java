package com.forgeflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forgeflow.dao.domain.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}

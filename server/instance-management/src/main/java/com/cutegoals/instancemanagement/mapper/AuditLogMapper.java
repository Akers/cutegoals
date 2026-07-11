package com.cutegoals.instancemanagement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.instance.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus Mapper for audit_log table.
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}

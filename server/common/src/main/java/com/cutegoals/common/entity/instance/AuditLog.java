package com.cutegoals.common.entity.instance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志表 entity
 */
@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("actor_id")
    private Long actorId;

    @TableField("actor_type")
    private String actorType;

    @TableField("event_type")
    private String eventType;

    @TableField("result")
    private String result;

    @TableField("object_type")
    private String objectType;

    @TableField("object_id")
    private String objectId;

    @TableField("summary")
    private String summary;

    @TableField("request_id")
    private String requestId;

    @TableField("metadata")
    private String metadata;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

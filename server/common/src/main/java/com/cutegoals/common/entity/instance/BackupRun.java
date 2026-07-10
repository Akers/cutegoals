package com.cutegoals.common.entity.instance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备份运行记录表 entity
 */
@Data
@TableName("backup_run")
public class BackupRun {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("status")
    private String status;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("checksum")
    private String checksum;

    @TableField("app_version")
    private String appVersion;

    @TableField("schema_version")
    private String schemaVersion;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

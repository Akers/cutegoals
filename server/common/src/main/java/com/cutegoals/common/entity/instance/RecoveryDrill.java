package com.cutegoals.common.entity.instance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 恢复演练结果表 entity
 */
@Data
@TableName("recovery_drill")
public class RecoveryDrill {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("success")
    private Boolean success;

    @TableField("rpo_seconds")
    private Integer rpoSeconds;

    @TableField("rto_seconds")
    private Integer rtoSeconds;

    @TableField("backup_used")
    private String backupUsed;

    @TableField("details")
    private String details;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

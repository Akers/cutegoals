package com.cutegoals.common.entity.task;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务提交尝试表 entity（不可变）
 */
@Data
@TableName("task_attempt")
public class TaskAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("assignment_id")
    private Long assignmentId;

    @TableField("child_id")
    private Long childId;

    @TableField("attempt_number")
    private Integer attemptNumber;

    @TableField("content")
    private String content;

    @TableField("attachments")
    private String attachments;

    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    @TableField("is_late")
    private Boolean isLate;

    @TableField("late_policy_applied")
    private String latePolicyApplied;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

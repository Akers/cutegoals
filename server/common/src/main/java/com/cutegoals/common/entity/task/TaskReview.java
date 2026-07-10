package com.cutegoals.common.entity.task;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务审核记录表 entity（不可变）
 */
@Data
@TableName("task_review")
public class TaskReview {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("attempt_id")
    private Long attemptId;

    @TableField("assignment_id")
    private Long assignmentId;

    @TableField("reviewer_id")
    private Long reviewerId;

    @TableField("decision")
    private String decision;

    @TableField("reason")
    private String reason;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

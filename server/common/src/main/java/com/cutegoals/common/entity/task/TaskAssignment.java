package com.cutegoals.common.entity.task;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务分配表 entity（含快照字段）
 */
@Data
@TableName("task_assignment")
public class TaskAssignment {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("family_id")
    private Long familyId;

    @TableField("template_id")
    private Long templateId;

    @TableField("child_id")
    private Long childId;

    @TableField("difficulty_id")
    private Long difficultyId;

    @TableField("deadline")
    private LocalDateTime deadline;

    @TableField("status")
    private String status;

    @TableField("late_policy")
    private String latePolicy;

    @TableField("cancelled")
    private Boolean cancelled;

    @TableField("cancelled_at")
    private LocalDateTime cancelledAt;

    @TableField("cancelled_reason")
    private String cancelledReason;

    @TableField("cancelled_by")
    private Long cancelledBy;

    @TableField("version")
    private Integer version;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField("occurrence_key")
    private String occurrenceKey;

    // -- Snapshot fields --
    @TableField("snapshot_template_name")
    private String snapshotTemplateName;

    @TableField("snapshot_template_description")
    private String snapshotTemplateDescription;

    @TableField("snapshot_template_category")
    private String snapshotTemplateCategory;

    @TableField("snapshot_template_icon")
    private String snapshotTemplateIcon;

    @TableField("snapshot_difficulty_name")
    private String snapshotDifficultyName;

    @TableField("snapshot_difficulty_reward")
    private Integer snapshotDifficultyReward;

    @TableField("submission_count")
    private Integer submissionCount;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

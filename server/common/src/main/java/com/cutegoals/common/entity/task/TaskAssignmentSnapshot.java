package com.cutegoals.common.entity.task;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务分配快照表 entity（历史版本追踪）
 */
@Data
@TableName("task_assignment_snapshot")
public class TaskAssignmentSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("assignment_id")
    private Long assignmentId;

    @TableField("template_name")
    private String templateName;

    @TableField("template_description")
    private String templateDescription;

    @TableField("template_category")
    private String templateCategory;

    @TableField("template_icon")
    private String templateIcon;

    @TableField("difficulty_name")
    private String difficultyName;

    @TableField("difficulty_reward_points")
    private Integer difficultyRewardPoints;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

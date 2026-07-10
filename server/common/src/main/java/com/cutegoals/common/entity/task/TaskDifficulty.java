package com.cutegoals.common.entity.task;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务难度表 entity
 */
@Data
@TableName("task_difficulty")
public class TaskDifficulty {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("template_id")
    private Long templateId;

    @TableField("name")
    private String name;

    @TableField("display_order")
    private Integer displayOrder;

    @TableField("reward_points")
    private Integer rewardPoints;

    @TableField("enabled")
    private Boolean enabled;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

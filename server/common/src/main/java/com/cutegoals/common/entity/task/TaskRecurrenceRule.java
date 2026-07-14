package com.cutegoals.common.entity.task;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 周期规则表 entity
 *
 * @deprecated Use {@link com.cutegoals.common.entity.task.TaskTemplate#typeConfig}
 *             for frequency information. This entity is kept for backward compatibility
 *             but new code should read frequency from TaskTemplate.typeConfig directly.
 */
@Deprecated
@Data
@TableName("task_recurrence_rule")
public class TaskRecurrenceRule {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("template_id")
    private Long templateId;

    @TableField("rule_type")
    private String ruleType;

    @TableField("custom_weekdays")
    private String customWeekdays;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

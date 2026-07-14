package com.cutegoals.common.entity.task;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务模板表 entity
 */
@Data
@TableName("task_template")
public class TaskTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("family_id")
    private Long familyId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("icon")
    private String icon;

    @TableField("category")
    private String category;

    @TableField("version")
    private Integer version;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("deleted")
    private Boolean deleted;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    @TableField("deleted_by")
    private Long deletedBy;

    @TableField("task_type")
    private String taskType;

    @TableField(value = "type_config", typeHandler = JacksonTypeHandler.class)
    private String typeConfig;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

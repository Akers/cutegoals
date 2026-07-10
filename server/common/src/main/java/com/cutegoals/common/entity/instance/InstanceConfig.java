package com.cutegoals.common.entity.instance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实例配置表 entity
 */
@Data
@TableName("instance_config")
public class InstanceConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("config_key")
    private String configKey;

    @TableField("config_value")
    private String configValue;

    @TableField("masked")
    private Boolean masked;

    @TableField("description")
    private String description;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

package com.cutegoals.common.entity.prize;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 奖品表 entity
 */
@Data
@TableName("prize")
public class Prize {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("family_id")
    private Long familyId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("image")
    private String image;

    @TableField("points_cost")
    private Integer pointsCost;

    @TableField("stock")
    private Integer stock;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("deleted")
    private Boolean deleted;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

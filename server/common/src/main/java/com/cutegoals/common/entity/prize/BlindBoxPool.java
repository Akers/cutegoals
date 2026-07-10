package com.cutegoals.common.entity.prize;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 盲盒奖池表 entity
 */
@Data
@TableName("blind_box_pool")
public class BlindBoxPool {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("family_id")
    private Long familyId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("cost_points")
    private Integer costPoints;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("deleted")
    private Boolean deleted;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    @TableField("availability_version")
    private String availabilityVersion;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

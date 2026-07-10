package com.cutegoals.common.entity.family;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 孩子档案表 entity
 */
@Data
@TableName("child_profile")
public class ChildProfile {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("family_id")
    private Long familyId;

    @TableField("nickname")
    private String nickname;

    @TableField("avatar")
    private String avatar;

    @TableField("pin_hash")
    private String pinHash;

    @TableField("birth_year")
    private Integer birthYear;

    @TableField("age_group")
    private String ageGroup;

    @TableField("status")
    private String status;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

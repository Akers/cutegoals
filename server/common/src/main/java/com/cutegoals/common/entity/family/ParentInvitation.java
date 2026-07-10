package com.cutegoals.common.entity.family;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家长邀请表 entity
 */
@Data
@TableName("parent_invitation")
public class ParentInvitation {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("family_id")
    private Long familyId;

    @TableField("inviter_id")
    private Long inviterId;

    @TableField("target_phone")
    private String targetPhone;

    @TableField("secret_hash")
    private String secretHash;

    @TableField("status")
    private String status;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

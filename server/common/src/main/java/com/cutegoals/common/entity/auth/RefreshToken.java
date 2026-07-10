package com.cutegoals.common.entity.auth;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 刷新令牌表 entity（支持家族链撤销）
 */
@Data
@TableName("refresh_token")
public class RefreshToken {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("token_hash")
    private String tokenHash;

    @TableField("session_id")
    private String sessionId;

    @TableField("family_id")
    private String familyId;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("revoked")
    private Boolean revoked;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

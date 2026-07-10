package com.cutegoals.common.entity.auth;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话表 entity
 */
@Data
@TableName("session")
public class Session {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("account_id")
    private Long accountId;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("device_fingerprint")
    private String deviceFingerprint;

    @TableField("revoked")
    private Boolean revoked;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

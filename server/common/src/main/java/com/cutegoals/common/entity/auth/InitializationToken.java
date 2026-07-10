package com.cutegoals.common.entity.auth;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 初始化令牌表 entity
 */
@Data
@TableName("initialization_token")
public class InitializationToken {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("token_hash")
    private String tokenHash;

    @TableField("consumed")
    private Boolean consumed;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

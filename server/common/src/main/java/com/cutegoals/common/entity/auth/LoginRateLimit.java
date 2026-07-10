package com.cutegoals.common.entity.auth;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录限流表 entity
 */
@Data
@TableName("login_rate_limit")
public class LoginRateLimit {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("identifier")
    private String identifier;

    @TableField("attempt_count")
    private Integer attemptCount;

    @TableField("window_start")
    private LocalDateTime windowStart;

    @TableField("locked_until")
    private LocalDateTime lockedUntil;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

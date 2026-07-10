package com.cutegoals.common.entity.auth;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号表 entity
 */
@Data
@TableName("account")
public class Account {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("phone")
    private String phone;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

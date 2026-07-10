package com.cutegoals.common.entity.auth;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色绑定表 entity
 */
@Data
@TableName("role_binding")
public class RoleBinding {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("account_id")
    private Long accountId;

    @TableField("role")
    private String role;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

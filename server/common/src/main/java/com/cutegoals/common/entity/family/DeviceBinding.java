package com.cutegoals.common.entity.family;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备绑定表 entity
 */
@Data
@TableName("device_binding")
public class DeviceBinding {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("family_id")
    private Long familyId;

    @TableField("device_id")
    private String deviceId;

    @TableField("credential_hash")
    private String credentialHash;

    @TableField("status")
    private String status;

    @TableField("bound_by")
    private Long boundBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

package com.cutegoals.common.entity.points;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分余额表 entity（含乐观锁 version）
 */
@Data
@TableName("points_balance")
public class PointsBalance {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("child_id")
    private Long childId;

    @TableField("balance")
    private Integer balance;

    @TableField("total_earned")
    private Integer totalEarned;

    @TableField("version")
    @Version
    private Integer version;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

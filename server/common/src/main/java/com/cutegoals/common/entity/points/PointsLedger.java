package com.cutegoals.common.entity.points;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水表 entity（不可变）
 */
@Data
@TableName("points_ledger")
public class PointsLedger {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("child_id")
    private Long childId;

    @TableField("type")
    private String type;

    @TableField("amount")
    private Integer amount;

    @TableField("balance_after")
    private Integer balanceAfter;

    @TableField("business_ref")
    private String businessRef;

    @TableField("source_snapshot")
    private String sourceSnapshot;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("reason")
    private String reason;

    @TableField("refund_source_id")
    private Long refundSourceId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

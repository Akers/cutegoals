package com.cutegoals.common.entity.exchange;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 兑换记录表 entity
 */
@Data
@TableName("exchange")
public class Exchange {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("child_id")
    private Long childId;

    @TableField("family_id")
    private Long familyId;

    @TableField("type")
    private String type;

    @TableField("status")
    private String status;

    @TableField("cost_points")
    private Integer costPoints;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField("prize_id")
    private Long prizeId;

    @TableField("pool_id")
    private Long poolId;

    @TableField("result_prize_id")
    private Long resultPrizeId;

    @TableField("fulfilled_at")
    private LocalDateTime fulfilledAt;

    @TableField("fulfilled_by")
    private Long fulfilledBy;

    @TableField("cancelled_at")
    private LocalDateTime cancelledAt;

    @TableField("cancelled_by")
    private Long cancelledBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

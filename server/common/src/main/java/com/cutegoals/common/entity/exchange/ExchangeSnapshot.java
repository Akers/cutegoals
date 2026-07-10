package com.cutegoals.common.entity.exchange;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 兑换快照表 entity（不可变）
 */
@Data
@TableName("exchange_snapshot")
public class ExchangeSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("exchange_id")
    private Long exchangeId;

    @TableField("prize_name")
    private String prizeName;

    @TableField("prize_image")
    private String prizeImage;

    @TableField("prize_description")
    private String prizeDescription;

    @TableField("points_cost")
    private Integer pointsCost;

    @TableField("pool_name")
    private String poolName;

    @TableField("pool_cost_points")
    private Integer poolCostPoints;

    @TableField("availability_version")
    private String availabilityVersion;

    @TableField("candidate_probabilities")
    private String candidateProbabilities;

    @TableField("drawn_prize_name")
    private String drawnPrizeName;

    @TableField("drawn_prize_image")
    private String drawnPrizeImage;

    @TableField("drawn_probability")
    private BigDecimal drawnProbability;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

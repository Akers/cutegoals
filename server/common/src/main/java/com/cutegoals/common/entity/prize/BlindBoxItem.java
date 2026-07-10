package com.cutegoals.common.entity.prize;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 盲盒奖品项表 entity
 */
@Data
@TableName("blind_box_item")
public class BlindBoxItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("pool_id")
    private Long poolId;

    @TableField("prize_id")
    private Long prizeId;

    @TableField("weight")
    private Integer weight;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

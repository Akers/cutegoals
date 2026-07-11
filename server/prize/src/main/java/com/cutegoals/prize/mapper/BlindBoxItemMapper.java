package com.cutegoals.prize.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.prize.BlindBoxItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface BlindBoxItemMapper extends BaseMapper<BlindBoxItem> {

    @Select("SELECT * FROM blind_box_item WHERE pool_id = #{poolId}")
    List<BlindBoxItem> findByPoolId(@Param("poolId") Long poolId);

    @Select("SELECT * FROM blind_box_item WHERE pool_id = #{poolId} AND prize_id = #{prizeId}")
    Optional<BlindBoxItem> findByPoolAndPrize(@Param("poolId") Long poolId, @Param("prizeId") Long prizeId);

    @Select("SELECT COUNT(1) FROM blind_box_item WHERE pool_id = #{poolId}")
    int countByPoolId(@Param("poolId") Long poolId);

    @Select("DELETE FROM blind_box_item WHERE pool_id = #{poolId}")
    void deleteByPoolId(@Param("poolId") Long poolId);
}

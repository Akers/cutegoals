package com.cutegoals.prize.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.prize.Prize;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

@Mapper
public interface PrizeMapper extends BaseMapper<Prize> {

    @Select("SELECT * FROM prize WHERE id = #{id}")
    Optional<Prize> findById(@Param("id") Long id);

    @Select("SELECT * FROM prize WHERE id = #{id} AND family_id = #{familyId}")
    Optional<Prize> findByIdAndFamily(@Param("id") Long id, @Param("familyId") Long familyId);

    @Select("SELECT * FROM prize WHERE id = #{id} AND family_id = #{familyId} FOR UPDATE")
    Optional<Prize> findByIdAndFamilyForUpdate(@Param("id") Long id, @Param("familyId") Long familyId);

    @Select("SELECT * FROM prize WHERE id = #{id} AND family_id = #{familyId} " +
            "AND enabled = TRUE AND deleted = FALSE AND stock > 0")
    Optional<Prize> findAvailableByIdAndFamily(@Param("id") Long id, @Param("familyId") Long familyId);

    @Update("UPDATE prize SET stock = stock - 1 WHERE id = #{id} AND stock > 0")
    int decrementStock(@Param("id") Long id);

    @Update("UPDATE prize SET stock = stock + 1 WHERE id = #{id}")
    int incrementStock(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM exchange WHERE (prize_id = #{prizeId} OR result_prize_id = #{prizeId})")
    int countExchangesByPrizeId(@Param("prizeId") Long prizeId);
}

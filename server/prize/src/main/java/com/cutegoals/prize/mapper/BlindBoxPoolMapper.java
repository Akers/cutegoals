package com.cutegoals.prize.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.prize.BlindBoxPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface BlindBoxPoolMapper extends BaseMapper<BlindBoxPool> {

    @Select("SELECT * FROM blind_box_pool WHERE id = #{id}")
    Optional<BlindBoxPool> findById(@Param("id") Long id);

    @Select("SELECT * FROM blind_box_pool WHERE id = #{id} AND family_id = #{familyId}")
    Optional<BlindBoxPool> findByIdAndFamily(@Param("id") Long id, @Param("familyId") Long familyId);

    @Select("SELECT * FROM blind_box_pool WHERE id = #{id} AND family_id = #{familyId} FOR UPDATE")
    Optional<BlindBoxPool> findByIdAndFamilyForUpdate(@Param("id") Long id, @Param("familyId") Long familyId);
}

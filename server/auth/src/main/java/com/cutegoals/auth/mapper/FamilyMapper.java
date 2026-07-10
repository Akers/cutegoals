package com.cutegoals.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.family.Family;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * MyBatis-Plus Mapper for family table.
 */
@Mapper
public interface FamilyMapper extends BaseMapper<Family> {

    @Select("SELECT * FROM family WHERE id = #{id}")
    Optional<Family> findById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM family")
    long countFamilies();
}

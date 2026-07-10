package com.cutegoals.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.family.Family;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis-Plus Mapper for family table (used during initialization).
 */
@Mapper
public interface FamilyMapper extends BaseMapper<Family> {

    @Select("SELECT COUNT(*) FROM family")
    long countFamilies();
}

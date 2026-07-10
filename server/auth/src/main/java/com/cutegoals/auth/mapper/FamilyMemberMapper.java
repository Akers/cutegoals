package com.cutegoals.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.family.FamilyMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * MyBatis-Plus Mapper for family_member table.
 */
@Mapper
public interface FamilyMemberMapper extends BaseMapper<FamilyMember> {

    @Select("SELECT * FROM family_member WHERE account_id = #{accountId} AND role = #{role}")
    Optional<FamilyMember> findByAccountIdAndRole(@Param("accountId") Long accountId, @Param("role") String role);
}

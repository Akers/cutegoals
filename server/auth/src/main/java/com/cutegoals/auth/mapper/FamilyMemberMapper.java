package com.cutegoals.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.family.FamilyMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus Mapper for family_member table.
 */
@Mapper
public interface FamilyMemberMapper extends BaseMapper<FamilyMember> {

    @Select("SELECT * FROM family_member WHERE id = #{id}")
    Optional<FamilyMember> findById(@Param("id") Long id);

    @Select("SELECT * FROM family_member WHERE account_id = #{accountId} AND role = #{role}")
    Optional<FamilyMember> findByAccountIdAndRole(@Param("accountId") Long accountId, @Param("role") String role);

    @Select("SELECT * FROM family_member WHERE family_id = #{familyId} AND role = #{role} AND status = 'ACTIVE'")
    List<FamilyMember> findActiveByFamilyIdAndRole(@Param("familyId") Long familyId, @Param("role") String role);

    @Select("SELECT COUNT(*) FROM family_member WHERE family_id = #{familyId} AND role = #{role} AND status = 'ACTIVE'")
    long countActiveByFamilyIdAndRole(@Param("familyId") Long familyId, @Param("role") String role);

    @Select("SELECT * FROM family_member WHERE family_id = #{familyId} AND status = 'ACTIVE'")
    List<FamilyMember> findActiveByFamilyId(@Param("familyId") Long familyId);

    @Update("UPDATE family_member SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}

package com.cutegoals.task.mapper;

import com.cutegoals.common.entity.family.ChildProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TaskChildMapper {

    @Select("SELECT * FROM child_profile WHERE id = #{id}")
    Optional<ChildProfile> findById(@Param("id") Long id);

    @Select("SELECT * FROM child_profile WHERE family_id = #{familyId} AND status = 'ACTIVE'")
    List<ChildProfile> findActiveByFamilyId(@Param("familyId") Long familyId);

    @Select("SELECT cp.* FROM child_profile cp " +
            "JOIN family_member fm ON fm.family_id = cp.family_id " +
            "WHERE fm.account_id = #{accountId} AND fm.role = 'CHILD' AND fm.status = 'ACTIVE' AND cp.status = 'ACTIVE'")
    Optional<ChildProfile> findByAccountId(@Param("accountId") Long accountId);
}

package com.cutegoals.family.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.family.ChildProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus Mapper for child_profile table.
 */
@Mapper
public interface ChildProfileMapper extends BaseMapper<ChildProfile> {

    @Select("SELECT * FROM child_profile WHERE id = #{id}")
    Optional<ChildProfile> findById(@Param("id") Long id);

    @Select("SELECT * FROM child_profile WHERE family_id = #{familyId} AND status != 'DELETED'")
    List<ChildProfile> findActiveByFamilyId(@Param("familyId") Long familyId);

    @Select("SELECT * FROM child_profile WHERE family_id = #{familyId} AND status = 'ACTIVE'")
    List<ChildProfile> findActiveOnlyByFamilyId(@Param("familyId") Long familyId);

    @Select("SELECT COUNT(*) FROM child_profile WHERE family_id = #{familyId} AND status != 'DELETED' AND pin_hash = #{pinHash}")
    long countByPinHash(@Param("familyId") Long familyId, @Param("pinHash") String pinHash);

    @Update("UPDATE child_profile SET status = #{status}, deleted_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE child_profile SET nickname = '已删除孩子', avatar = NULL, pin_hash = NULL, birth_year = NULL, age_group = NULL, status = 'DELETED', deleted_at = NOW() WHERE id = #{id}")
    int anonymizeDelete(@Param("id") Long id);
}

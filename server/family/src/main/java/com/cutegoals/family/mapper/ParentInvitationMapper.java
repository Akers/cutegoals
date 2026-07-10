package com.cutegoals.family.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.family.ParentInvitation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * MyBatis-Plus Mapper for parent_invitation table.
 */
@Mapper
public interface ParentInvitationMapper extends BaseMapper<ParentInvitation> {

    @Select("SELECT * FROM parent_invitation WHERE id = #{id}")
    Optional<ParentInvitation> findById(@Param("id") Long id);

    @Select("SELECT * FROM parent_invitation WHERE secret_hash = #{secretHash}")
    Optional<ParentInvitation> findBySecretHash(@Param("secretHash") String secretHash);

    @Select("SELECT * FROM parent_invitation WHERE inviter_id = #{inviterId} AND idempotency_key = #{idempotencyKey}")
    Optional<ParentInvitation> findByIdempotencyKey(@Param("inviterId") Long inviterId,
                                                     @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT * FROM parent_invitation WHERE family_id = #{familyId} AND target_phone = #{targetPhone} AND status = 'PENDING' AND expires_at > NOW()")
    Optional<ParentInvitation> findPendingByFamilyAndPhone(@Param("familyId") Long familyId,
                                                            @Param("targetPhone") String targetPhone);

    @Update("UPDATE parent_invitation SET status = #{status} WHERE id = #{id} AND status = 'PENDING'")
    int updateStatusIfPending(@Param("id") Long id, @Param("status") String status);
}

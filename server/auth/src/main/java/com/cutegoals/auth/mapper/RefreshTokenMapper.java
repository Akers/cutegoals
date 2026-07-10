package com.cutegoals.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.auth.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * MyBatis-Plus Mapper for refresh_token table.
 */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {

    @Select("SELECT * FROM refresh_token WHERE token_hash = #{tokenHash}")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Update("UPDATE refresh_token SET revoked = TRUE WHERE id = #{id}")
    int revokeById(@Param("id") Long id);

    @Update("UPDATE refresh_token SET revoked = TRUE WHERE family_id = #{familyId} AND revoked = FALSE")
    int revokeFamily(@Param("familyId") String familyId);

    @Update("UPDATE refresh_token SET revoked = TRUE WHERE session_id = #{sessionId} AND revoked = FALSE")
    int revokeBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT family_id FROM refresh_token WHERE token_hash = #{tokenHash}")
    Optional<String> findFamilyIdByTokenHash(@Param("tokenHash") String tokenHash);
}

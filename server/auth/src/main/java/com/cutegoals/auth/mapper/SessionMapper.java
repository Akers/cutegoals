package com.cutegoals.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.auth.Session;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * MyBatis-Plus Mapper for session table.
 */
@Mapper
public interface SessionMapper extends BaseMapper<Session> {

    @Select("SELECT * FROM session WHERE session_id = #{sessionId}")
    Optional<Session> findBySessionId(@Param("sessionId") String sessionId);

    @Update("UPDATE session SET revoked = TRUE WHERE session_id = #{sessionId}")
    int revokeSession(@Param("sessionId") String sessionId);

    @Update("UPDATE session SET revoked = TRUE WHERE account_id = #{accountId} AND revoked = FALSE")
    int revokeAllByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT COUNT(*) FROM session WHERE account_id = #{accountId} AND revoked = FALSE")
    long countActiveSessions(@Param("accountId") Long accountId);

    @Update("UPDATE session SET revoked = TRUE WHERE device_fingerprint = #{deviceFingerprint} AND revoked = FALSE")
    int revokeByDeviceFingerprint(@Param("deviceFingerprint") String deviceFingerprint);
}

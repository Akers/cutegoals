package com.cutegoals.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.auth.InitializationToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * MyBatis-Plus Mapper for initialization_token table.
 */
@Mapper
public interface InitializationTokenMapper extends BaseMapper<InitializationToken> {

    @Select("SELECT * FROM initialization_token WHERE token_hash = #{tokenHash}")
    Optional<InitializationToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Update("UPDATE initialization_token SET consumed = TRUE WHERE id = #{id} AND consumed = FALSE")
    int consumeToken(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM initialization_token WHERE consumed = TRUE")
    long countConsumedTokens();

    @Select("SELECT COUNT(*) FROM initialization_token WHERE consumed = FALSE AND expires_at > NOW()")
    long countValidTokens();
}

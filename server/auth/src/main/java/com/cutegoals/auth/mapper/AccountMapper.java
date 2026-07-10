package com.cutegoals.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.auth.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * MyBatis-Plus Mapper for account table.
 */
@Mapper
public interface AccountMapper extends BaseMapper<Account> {

    @Select("SELECT * FROM account WHERE phone = #{phone}")
    Optional<Account> findByPhone(@Param("phone") String phone);

    @Select("SELECT * FROM account WHERE id = #{id}")
    Optional<Account> findById(@Param("id") Long id);

    @Update("UPDATE account SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Select("SELECT COUNT(*) FROM account")
    long countAccounts();
}

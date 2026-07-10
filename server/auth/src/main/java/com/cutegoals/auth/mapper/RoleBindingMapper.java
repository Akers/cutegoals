package com.cutegoals.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.auth.RoleBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus Mapper for role_binding table.
 */
@Mapper
public interface RoleBindingMapper extends BaseMapper<RoleBinding> {

    @Select("SELECT role FROM role_binding WHERE account_id = #{accountId}")
    List<String> findRolesByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT COUNT(*) FROM role_binding WHERE role = #{role}")
    long countByRole(@Param("role") String role);
}

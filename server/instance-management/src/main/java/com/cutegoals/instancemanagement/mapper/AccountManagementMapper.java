package com.cutegoals.instancemanagement.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cutegoals.common.entity.auth.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * Additional Mapper for admin account management operations.
 */
@Mapper
public interface AccountManagementMapper {

    @Select("SELECT * FROM account ORDER BY created_at DESC")
    List<Account> findAllAccounts();

    @Select("SELECT * FROM account ORDER BY created_at DESC")
    IPage<Account> findAccountsWithPage(IPage<Account> page);

    @Select("SELECT * FROM account WHERE id = #{id} FOR UPDATE")
    Optional<Account> findByIdWithLock(@Param("id") Long id);

    @Select("SELECT * FROM account WHERE id = #{id}")
    Optional<Account> findById(@Param("id") Long id);

    @Update("UPDATE account SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Select("SELECT COUNT(*) FROM account WHERE status = 'ACTIVE'")
    long countActiveAccounts();

    @Select("SELECT COUNT(*) FROM role_binding rb "
            + "JOIN account a ON a.id = rb.account_id "
            + "WHERE rb.role = 'INSTANCE_ADMIN' AND a.status = 'ACTIVE' "
            + "FOR UPDATE OF rb")
    long countActiveInstanceAdminsWithLock();

    @Select("SELECT COUNT(*) FROM role_binding rb "
            + "JOIN account a ON a.id = rb.account_id "
            + "WHERE rb.role = 'INSTANCE_ADMIN' AND a.status = 'ACTIVE'")
    long countActiveInstanceAdmins();

    @Select("SELECT COUNT(*) FROM role_binding rb "
            + "JOIN account a ON a.id = rb.account_id "
            + "JOIN family_member fm ON fm.account_id = a.id "
            + "WHERE rb.role = 'PARENT' AND a.status = 'ACTIVE' AND fm.status = 'ACTIVE' "
            + "FOR UPDATE OF rb")
    long countActiveParentsWithLock();

    @Select("SELECT COUNT(*) FROM role_binding rb "
            + "JOIN account a ON a.id = rb.account_id "
            + "JOIN family_member fm ON fm.account_id = a.id "
            + "WHERE rb.role = 'PARENT' AND a.status = 'ACTIVE' AND fm.status = 'ACTIVE'")
    long countActiveParents();
}

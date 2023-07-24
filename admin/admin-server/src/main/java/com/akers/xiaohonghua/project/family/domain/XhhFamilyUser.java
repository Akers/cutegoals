package com.akers.xiaohonghua.project.family.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.akers.xiaohonghua.framework.aspectj.lang.annotation.Excel;
import com.akers.xiaohonghua.framework.web.domain.BaseEntity;

/**
 * 家庭用户关联表对象 xhh_family_user
 * 
 * @author akers
 * @date 2023-07-10
 */
public class XhhFamilyUser extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 租户号 */
    private Long id;

    /** 乐观锁 */
    private Long version;

    /** 家庭ID */
    private Long familyId;

    /** 用户ID */
    private Long usrId;

    /** 角色代码 */
    @Excel(name = "角色代码")
    private String roleCode;

    /** 用户名称 */
    @Excel(name = "用户名称")
    private String usrName;

    /** 成员名称 */
    @Excel(name = "成员名称")
    private String roleName;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }
    public void setVersion(Long version) 
    {
        this.version = version;
    }

    public Long getVersion() 
    {
        return version;
    }
    public void setFamilyId(Long familyId) 
    {
        this.familyId = familyId;
    }

    public Long getFamilyId() 
    {
        return familyId;
    }
    public void setUsrId(Long usrId) 
    {
        this.usrId = usrId;
    }

    public Long getUsrId() 
    {
        return usrId;
    }
    public void setRoleCode(String roleCode) 
    {
        this.roleCode = roleCode;
    }

    public String getRoleCode() 
    {
        return roleCode;
    }
    public void setUsrName(String usrName) 
    {
        this.usrName = usrName;
    }

    public String getUsrName() 
    {
        return usrName;
    }
    public void setRoleName(String roleName) 
    {
        this.roleName = roleName;
    }

    public String getRoleName() 
    {
        return roleName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("version", getVersion())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("familyId", getFamilyId())
            .append("usrId", getUsrId())
            .append("roleCode", getRoleCode())
            .append("usrName", getUsrName())
            .append("roleName", getRoleName())
            .toString();
    }
}

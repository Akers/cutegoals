package com.akers.xiaohonghua.project.family.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.akers.xiaohonghua.framework.aspectj.lang.annotation.Excel;
import com.akers.xiaohonghua.framework.web.domain.BaseEntity;

/**
 * 家庭信息管理对象 xhh_family
 * 
 * @author akers
 * @date 2023-07-24
 */
public class XhhFamily extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 租户号 */
    private Long familyId;

    /** 乐观锁 */
    private Long version;

    /** 家庭名称 */
    @Excel(name = "家庭名称")
    private String familyName;

    /** 家庭地址 */
    @Excel(name = "家庭地址")
    private String familyAddress;

    /** 关联用户id */
    private Long usrId;

    public void setFamilyId(Long familyId) 
    {
        this.familyId = familyId;
    }

    public Long getFamilyId() 
    {
        return familyId;
    }
    public void setVersion(Long version) 
    {
        this.version = version;
    }

    public Long getVersion() 
    {
        return version;
    }
    public void setFamilyName(String familyName) 
    {
        this.familyName = familyName;
    }

    public String getFamilyName() 
    {
        return familyName;
    }
    public void setFamilyAddress(String familyAddress) 
    {
        this.familyAddress = familyAddress;
    }

    public String getFamilyAddress() 
    {
        return familyAddress;
    }
    public void setUsrId(Long usrId) 
    {
        this.usrId = usrId;
    }

    public Long getUsrId() 
    {
        return usrId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("familyId", getFamilyId())
            .append("version", getVersion())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("familyName", getFamilyName())
            .append("familyAddress", getFamilyAddress())
            .append("usrId", getUsrId())
            .toString();
    }
}

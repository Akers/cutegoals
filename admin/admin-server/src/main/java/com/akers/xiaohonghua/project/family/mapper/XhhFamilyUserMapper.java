package com.akers.xiaohonghua.project.family.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import com.akers.xiaohonghua.project.family.domain.XhhFamilyUser;

/**
 * 用户家庭关联Mapper接口
 * 
 * @author ruoyi
 * @date 2023-07-24
 */
public interface XhhFamilyUserMapper 
{
    /**
     * 查询用户家庭关联
     * 
     * @param familyUserId 用户家庭关联主键
     * @return 用户家庭关联
     */
    public XhhFamilyUser selectXhhFamilyUserByFamilyUserId(Long familyUserId);

    /**
     * 查询用户家庭关联列表
     * 
     * @param xhhFamilyUser 用户家庭关联
     * @return 用户家庭关联集合
     */
    public List<XhhFamilyUser> selectXhhFamilyUserList(XhhFamilyUser xhhFamilyUser);
    public IPage<XhhFamilyUser> selectXhhFamilyUserList(IPage<?> page, XhhFamilyUser xhhFamilyUser);

    /**
     * 新增用户家庭关联
     * 
     * @param xhhFamilyUser 用户家庭关联
     * @return 结果
     */
    public int insertXhhFamilyUser(XhhFamilyUser xhhFamilyUser);

    /**
     * 修改用户家庭关联
     * 
     * @param xhhFamilyUser 用户家庭关联
     * @return 结果
     */
    public int updateXhhFamilyUser(XhhFamilyUser xhhFamilyUser);

    /**
     * 删除用户家庭关联
     * 
     * @param familyUserId 用户家庭关联主键
     * @return 结果
     */
    public int deleteXhhFamilyUserByFamilyUserId(Long familyUserId);

    /**
     * 批量删除用户家庭关联
     * 
     * @param familyUserIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteXhhFamilyUserByFamilyUserIds(Long[] familyUserIds);

    int deleteXhhFamilyUserByFamilyId(Long familyId);

    int deleteXhhFamilyUserByFamilyIds(Long[] familyIds);
}

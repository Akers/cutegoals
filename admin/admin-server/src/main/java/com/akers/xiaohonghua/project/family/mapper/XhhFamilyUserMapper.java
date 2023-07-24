package com.akers.xiaohonghua.project.family.mapper;

import java.util.List;
import com.akers.xiaohonghua.project.family.domain.XhhFamilyUser;

/**
 * 家庭用户关联表Mapper接口
 * 
 * @author akers
 * @date 2023-07-10
 */
public interface XhhFamilyUserMapper 
{
    /**
     * 查询家庭用户关联表
     * 
     * @param id 家庭用户关联表主键
     * @return 家庭用户关联表
     */
    public XhhFamilyUser selectXhhFamilyUserById(Long id);

    /**
     * 查询家庭用户关联表列表
     * 
     * @param xhhFamilyUser 家庭用户关联表
     * @return 家庭用户关联表集合
     */
    public List<XhhFamilyUser> selectXhhFamilyUserList(XhhFamilyUser xhhFamilyUser);

    /**
     * 新增家庭用户关联表
     * 
     * @param xhhFamilyUser 家庭用户关联表
     * @return 结果
     */
    public int insertXhhFamilyUser(XhhFamilyUser xhhFamilyUser);

    /**
     * 修改家庭用户关联表
     * 
     * @param xhhFamilyUser 家庭用户关联表
     * @return 结果
     */
    public int updateXhhFamilyUser(XhhFamilyUser xhhFamilyUser);

    /**
     * 删除家庭用户关联表
     * 
     * @param id 家庭用户关联表主键
     * @return 结果
     */
    public int deleteXhhFamilyUserById(Long id);

    /**
     * 批量删除家庭用户关联表
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteXhhFamilyUserByIds(Long[] ids);
}

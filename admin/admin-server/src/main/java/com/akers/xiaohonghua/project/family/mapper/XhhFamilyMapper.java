package com.akers.xiaohonghua.project.family.mapper;

import java.util.List;
import com.akers.xiaohonghua.project.family.domain.XhhFamily;
import com.akers.xiaohonghua.project.family.domain.XhhFamilyUser;

/**
 * 家庭信息Mapper接口
 * 
 * @author akers
 * @date 2023-07-10
 */
public interface XhhFamilyMapper 
{
    /**
     * 查询家庭信息
     * 
     * @param id 家庭信息主键
     * @return 家庭信息
     */
    public XhhFamily selectXhhFamilyById(Long id);

    /**
     * 查询家庭信息列表
     * 
     * @param xhhFamily 家庭信息
     * @return 家庭信息集合
     */
    public List<XhhFamily> selectXhhFamilyList(XhhFamily xhhFamily);

    /**
     * 新增家庭信息
     * 
     * @param xhhFamily 家庭信息
     * @return 结果
     */
    public int insertXhhFamily(XhhFamily xhhFamily);

    /**
     * 修改家庭信息
     * 
     * @param xhhFamily 家庭信息
     * @return 结果
     */
    public int updateXhhFamily(XhhFamily xhhFamily);

    /**
     * 删除家庭信息
     * 
     * @param id 家庭信息主键
     * @return 结果
     */
    public int deleteXhhFamilyById(Long id);

    /**
     * 批量删除家庭信息
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteXhhFamilyByIds(Long[] ids);

    /**
     * 批量删除家庭用户关联表
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteXhhFamilyUserByFamilyIds(Long[] ids);
    
    /**
     * 批量新增家庭用户关联表
     * 
     * @param xhhFamilyUserList 家庭用户关联表列表
     * @return 结果
     */
    public int batchXhhFamilyUser(List<XhhFamilyUser> xhhFamilyUserList);
    

    /**
     * 通过家庭信息主键删除家庭用户关联表信息
     * 
     * @param id 家庭信息ID
     * @return 结果
     */
    public int deleteXhhFamilyUserByFamilyId(Long id);
}

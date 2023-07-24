package com.akers.xiaohonghua.project.family.service;

import java.util.List;
import com.akers.xiaohonghua.project.family.domain.XhhFamily;

/**
 * 家庭信息Service接口
 * 
 * @author akers
 * @date 2023-07-10
 */
public interface IXhhFamilyService 
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
     * 批量删除家庭信息
     * 
     * @param ids 需要删除的家庭信息主键集合
     * @return 结果
     */
    public int deleteXhhFamilyByIds(Long[] ids);

    /**
     * 删除家庭信息信息
     * 
     * @param id 家庭信息主键
     * @return 结果
     */
    public int deleteXhhFamilyById(Long id);
}

package com.akers.xiaohonghua.project.family.service.impl;

import java.util.List;
import com.akers.xiaohonghua.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import com.akers.xiaohonghua.common.utils.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import com.akers.xiaohonghua.project.family.domain.XhhFamilyUser;
import com.akers.xiaohonghua.project.family.mapper.XhhFamilyMapper;
import com.akers.xiaohonghua.project.family.domain.XhhFamily;
import com.akers.xiaohonghua.project.family.service.IXhhFamilyService;

/**
 * 家庭信息Service业务层处理
 * 
 * @author akers
 * @date 2023-07-10
 */
@Service
public class XhhFamilyServiceImpl implements IXhhFamilyService 
{
    @Autowired
    private XhhFamilyMapper xhhFamilyMapper;

    /**
     * 查询家庭信息
     * 
     * @param id 家庭信息主键
     * @return 家庭信息
     */
    @Override
    public XhhFamily selectXhhFamilyById(Long id)
    {
        return xhhFamilyMapper.selectXhhFamilyById(id);
    }

    /**
     * 查询家庭信息列表
     * 
     * @param xhhFamily 家庭信息
     * @return 家庭信息
     */
    @Override
    public List<XhhFamily> selectXhhFamilyList(XhhFamily xhhFamily)
    {
        return xhhFamilyMapper.selectXhhFamilyList(xhhFamily);
    }

    /**
     * 新增家庭信息
     * 
     * @param xhhFamily 家庭信息
     * @return 结果
     */
    @Transactional
    @Override
    public int insertXhhFamily(XhhFamily xhhFamily)
    {
        xhhFamily.setCreateTime(DateUtils.getNowDate());
        int rows = xhhFamilyMapper.insertXhhFamily(xhhFamily);
        insertXhhFamilyUser(xhhFamily);
        return rows;
    }

    /**
     * 修改家庭信息
     * 
     * @param xhhFamily 家庭信息
     * @return 结果
     */
    @Transactional
    @Override
    public int updateXhhFamily(XhhFamily xhhFamily)
    {
        xhhFamily.setUpdateTime(DateUtils.getNowDate());
        xhhFamilyMapper.deleteXhhFamilyUserByFamilyId(xhhFamily.getId());
        insertXhhFamilyUser(xhhFamily);
        return xhhFamilyMapper.updateXhhFamily(xhhFamily);
    }

    /**
     * 批量删除家庭信息
     * 
     * @param ids 需要删除的家庭信息主键
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteXhhFamilyByIds(Long[] ids)
    {
        xhhFamilyMapper.deleteXhhFamilyUserByFamilyIds(ids);
        return xhhFamilyMapper.deleteXhhFamilyByIds(ids);
    }

    /**
     * 删除家庭信息信息
     * 
     * @param id 家庭信息主键
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteXhhFamilyById(Long id)
    {
        xhhFamilyMapper.deleteXhhFamilyUserByFamilyId(id);
        return xhhFamilyMapper.deleteXhhFamilyById(id);
    }

    /**
     * 新增家庭用户关联表信息
     * 
     * @param xhhFamily 家庭信息对象
     */
    public void insertXhhFamilyUser(XhhFamily xhhFamily)
    {
        List<XhhFamilyUser> xhhFamilyUserList = xhhFamily.getXhhFamilyUserList();
        Long id = xhhFamily.getId();
        if (StringUtils.isNotNull(xhhFamilyUserList))
        {
            List<XhhFamilyUser> list = new ArrayList<XhhFamilyUser>();
            for (XhhFamilyUser xhhFamilyUser : xhhFamilyUserList)
            {
                xhhFamilyUser.setFamilyId(id);
                list.add(xhhFamilyUser);
            }
            if (list.size() > 0)
            {
                xhhFamilyMapper.batchXhhFamilyUser(list);
            }
        }
    }
}

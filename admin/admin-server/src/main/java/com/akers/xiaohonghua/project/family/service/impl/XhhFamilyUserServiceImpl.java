package com.akers.xiaohonghua.project.family.service.impl;

import java.util.List;
import com.akers.xiaohonghua.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.akers.xiaohonghua.project.family.mapper.XhhFamilyUserMapper;
import com.akers.xiaohonghua.project.family.domain.XhhFamilyUser;
import com.akers.xiaohonghua.project.family.service.IXhhFamilyUserService;

/**
 * 家庭用户关联表Service业务层处理
 * 
 * @author akers
 * @date 2023-07-10
 */
@Service
public class XhhFamilyUserServiceImpl implements IXhhFamilyUserService 
{
    @Autowired
    private XhhFamilyUserMapper xhhFamilyUserMapper;

    /**
     * 查询家庭用户关联表
     * 
     * @param id 家庭用户关联表主键
     * @return 家庭用户关联表
     */
    @Override
    public XhhFamilyUser selectXhhFamilyUserById(Long id)
    {
        return xhhFamilyUserMapper.selectXhhFamilyUserById(id);
    }

    /**
     * 查询家庭用户关联表列表
     * 
     * @param xhhFamilyUser 家庭用户关联表
     * @return 家庭用户关联表
     */
    @Override
    public List<XhhFamilyUser> selectXhhFamilyUserList(XhhFamilyUser xhhFamilyUser)
    {
        return xhhFamilyUserMapper.selectXhhFamilyUserList(xhhFamilyUser);
    }

    /**
     * 新增家庭用户关联表
     * 
     * @param xhhFamilyUser 家庭用户关联表
     * @return 结果
     */
    @Override
    public int insertXhhFamilyUser(XhhFamilyUser xhhFamilyUser)
    {
        xhhFamilyUser.setCreateTime(DateUtils.getNowDate());
        return xhhFamilyUserMapper.insertXhhFamilyUser(xhhFamilyUser);
    }

    /**
     * 修改家庭用户关联表
     * 
     * @param xhhFamilyUser 家庭用户关联表
     * @return 结果
     */
    @Override
    public int updateXhhFamilyUser(XhhFamilyUser xhhFamilyUser)
    {
        xhhFamilyUser.setUpdateTime(DateUtils.getNowDate());
        return xhhFamilyUserMapper.updateXhhFamilyUser(xhhFamilyUser);
    }

    /**
     * 批量删除家庭用户关联表
     * 
     * @param ids 需要删除的家庭用户关联表主键
     * @return 结果
     */
    @Override
    public int deleteXhhFamilyUserByIds(Long[] ids)
    {
        return xhhFamilyUserMapper.deleteXhhFamilyUserByIds(ids);
    }

    /**
     * 删除家庭用户关联表信息
     * 
     * @param id 家庭用户关联表主键
     * @return 结果
     */
    @Override
    public int deleteXhhFamilyUserById(Long id)
    {
        return xhhFamilyUserMapper.deleteXhhFamilyUserById(id);
    }
}

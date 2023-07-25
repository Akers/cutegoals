package com.akers.xiaohonghua.project.family.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import com.akers.xiaohonghua.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.akers.xiaohonghua.project.family.mapper.XhhFamilyMapper;
import com.akers.xiaohonghua.project.family.domain.XhhFamily;
import com.akers.xiaohonghua.project.family.service.IXhhFamilyService;

/**
 * 家庭信息管理Service业务层处理
 * 
 * @author akers
 * @date 2023-07-24
 */
@Service
public class XhhFamilyServiceImpl implements IXhhFamilyService 
{
    @Autowired
    private XhhFamilyMapper xhhFamilyMapper;

    /**
     * 查询家庭信息管理
     * 
     * @param familyId 家庭信息管理主键
     * @return 家庭信息管理
     */
    @Override
    public XhhFamily selectXhhFamilyByFamilyId(Long familyId)
    {
        return xhhFamilyMapper.selectXhhFamilyByFamilyId(familyId);
    }

    /**
     * 查询家庭信息管理列表
     * 
     * @param xhhFamily 家庭信息管理
     * @return 家庭信息管理
     */
    @Override
	public List<XhhFamily> selectXhhFamilyList(XhhFamily xhhFamily) {
		return xhhFamilyMapper.selectXhhFamilyList(xhhFamily);
	}

	@Override
	public IPage<XhhFamily> selectXhhFamilyList(IPage<?> page, XhhFamily xhhFamily) {
	    return xhhFamilyMapper.selectXhhFamilyList(page, xhhFamily);
	}

    /**
     * 新增家庭信息管理
     * 
     * @param xhhFamily 家庭信息管理
     * @return 结果
     */
    @Override
    public int insertXhhFamily(XhhFamily xhhFamily)
    {
        xhhFamily.setCreateTime(DateUtils.getNowDate());
        return xhhFamilyMapper.insertXhhFamily(xhhFamily);
    }

    /**
     * 修改家庭信息管理
     * 
     * @param xhhFamily 家庭信息管理
     * @return 结果
     */
    @Override
    public int updateXhhFamily(XhhFamily xhhFamily)
    {
        xhhFamily.setUpdateTime(DateUtils.getNowDate());
        return xhhFamilyMapper.updateXhhFamily(xhhFamily);
    }

    /**
     * 批量删除家庭信息管理
     * 
     * @param familyIds 需要删除的家庭信息管理主键
     * @return 结果
     */
    @Override
    public int deleteXhhFamilyByFamilyIds(Long[] familyIds)
    {
        return xhhFamilyMapper.deleteXhhFamilyByFamilyIds(familyIds);
    }

    /**
     * 删除家庭信息管理信息
     * 
     * @param familyId 家庭信息管理主键
     * @return 结果
     */
    @Override
    public int deleteXhhFamilyByFamilyId(Long familyId)
    {
        return xhhFamilyMapper.deleteXhhFamilyByFamilyId(familyId);
    }
}

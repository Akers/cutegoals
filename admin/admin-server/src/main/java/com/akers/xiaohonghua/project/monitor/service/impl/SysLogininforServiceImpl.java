package com.akers.xiaohonghua.project.monitor.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.akers.xiaohonghua.project.monitor.domain.SysLogininfor;
import com.akers.xiaohonghua.project.monitor.mapper.SysLogininforMapper;
import com.akers.xiaohonghua.project.monitor.service.ISysLogininforService;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 系统访问日志情况信息 服务层处理
 * 
 * @author ruoyi
 */
@Service
public class SysLogininforServiceImpl extends ServiceImpl<SysLogininforMapper, SysLogininfor> implements ISysLogininforService
{

    @Autowired
    private SysLogininforMapper logininforMapper;

    /**
     * 新增系统登录日志
     * 
     * @param logininfor 访问日志对象
     */
    @Override
    public void insertLogininfor(SysLogininfor logininfor)
    {
        logininforMapper.insertLogininfor(logininfor);
    }

    /**
     * 查询系统登录日志集合
     * 
     * @param logininfor 访问日志对象
     * @return 登录记录集合
     */
    @Override
	public List<SysLogininfor> selectLogininforList(SysLogininfor logininfor) {
		return logininforMapper.selectLogininforList(logininfor);
	}

	@Override
	public IPage<SysLogininfor> selectLogininforList(IPage<?> page, SysLogininfor logininfor) {
	    return logininforMapper.selectLogininforList(page, logininfor);
	}

    /**
     * 批量删除系统登录日志
     * 
     * @param infoIds 需要删除的登录日志ID
     * @return 结果
     */
    @Override
    public int deleteLogininforByIds(Long[] infoIds)
    {
        return logininforMapper.deleteLogininforByIds(infoIds);
    }

    /**
     * 清空系统登录日志
     */
    @Override
    public void cleanLogininfor()
    {
        logininforMapper.cleanLogininfor();
    }
}

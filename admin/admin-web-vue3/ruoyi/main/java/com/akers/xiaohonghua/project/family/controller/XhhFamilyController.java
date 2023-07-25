package com.akers.xiaohonghua.project.family.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.akers.xiaohonghua.framework.aspectj.lang.annotation.Log;
import com.akers.xiaohonghua.framework.aspectj.lang.enums.BusinessType;
import com.akers.xiaohonghua.project.family.domain.XhhFamily;
import com.akers.xiaohonghua.project.family.service.IXhhFamilyService;
import com.akers.xiaohonghua.framework.web.controller.BaseController;
import com.akers.xiaohonghua.framework.web.domain.AjaxResult;
import com.akers.xiaohonghua.common.utils.poi.ExcelUtil;
import com.akers.xiaohonghua.framework.web.page.TableDataInfo;

/**
 * 家庭信息管理Controller
 * 
 * @author akers
 * @date 2023-07-24
 */
@RestController
@RequestMapping("/family/familyadmin")
public class XhhFamilyController extends BaseController
{
    @Autowired
    private IXhhFamilyService xhhFamilyService;

    /**
     * 查询家庭信息管理列表
     */
    @PreAuthorize("@ss.hasPermi('family:familyadmin:list')")
    @GetMapping("/list")
    public TableDataInfo list(XhhFamily xhhFamily)
    {
        IPage<XhhFamily> page = xhhFamilyService.selectXhhFamilyList(getPage(), xhhFamily);
        return getDataTable(page);
    }

    /**
     * 导出家庭信息管理列表
     */
    @PreAuthorize("@ss.hasPermi('family:familyadmin:export')")
    @Log(title = "家庭信息管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, XhhFamily xhhFamily)
    {
        List<XhhFamily> list = xhhFamilyService.selectXhhFamilyList(xhhFamily);
        ExcelUtil<XhhFamily> util = new ExcelUtil<XhhFamily>(XhhFamily.class);
        util.exportExcel(response, list, "家庭信息管理数据");
    }

    /**
     * 获取家庭信息管理详细信息
     */
    @PreAuthorize("@ss.hasPermi('family:familyadmin:query')")
    @GetMapping(value = "/{familyId}")
    public AjaxResult getInfo(@PathVariable("familyId") Long familyId)
    {
        return success(xhhFamilyService.selectXhhFamilyByFamilyId(familyId));
    }

    /**
     * 新增家庭信息管理
     */
    @PreAuthorize("@ss.hasPermi('family:familyadmin:add')")
    @Log(title = "家庭信息管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody XhhFamily xhhFamily)
    {
        return toAjax(xhhFamilyService.insertXhhFamily(xhhFamily));
    }

    /**
     * 修改家庭信息管理
     */
    @PreAuthorize("@ss.hasPermi('family:familyadmin:edit')")
    @Log(title = "家庭信息管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody XhhFamily xhhFamily)
    {
        return toAjax(xhhFamilyService.updateXhhFamily(xhhFamily));
    }

    /**
     * 删除家庭信息管理
     */
    @PreAuthorize("@ss.hasPermi('family:familyadmin:remove')")
    @Log(title = "家庭信息管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{familyIds}")
    public AjaxResult remove(@PathVariable Long[] familyIds)
    {
        return toAjax(xhhFamilyService.deleteXhhFamilyByFamilyIds(familyIds));
    }
}

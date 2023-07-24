package com.akers.xiaohonghua.project.family.controller;

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
import com.akers.xiaohonghua.project.family.domain.XhhFamilyUser;
import com.akers.xiaohonghua.project.family.service.IXhhFamilyUserService;
import com.akers.xiaohonghua.framework.web.controller.BaseController;
import com.akers.xiaohonghua.framework.web.domain.AjaxResult;
import com.akers.xiaohonghua.common.utils.poi.ExcelUtil;
import com.akers.xiaohonghua.framework.web.page.TableDataInfo;

/**
 * 家庭用户关联表Controller
 * 
 * @author akers
 * @date 2023-07-10
 */
@RestController
@RequestMapping("/family/FamilyUser")
public class XhhFamilyUserController extends BaseController
{
    @Autowired
    private IXhhFamilyUserService xhhFamilyUserService;

    /**
     * 查询家庭用户关联表列表
     */
    @PreAuthorize("@ss.hasPermi('family:FamilyUser:list')")
    @GetMapping("/list")
    public TableDataInfo list(XhhFamilyUser xhhFamilyUser)
    {
        startPage();
        List<XhhFamilyUser> list = xhhFamilyUserService.selectXhhFamilyUserList(xhhFamilyUser);
        return getDataTable(list);
    }

    /**
     * 导出家庭用户关联表列表
     */
    @PreAuthorize("@ss.hasPermi('family:FamilyUser:export')")
    @Log(title = "家庭用户关联表", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, XhhFamilyUser xhhFamilyUser)
    {
        List<XhhFamilyUser> list = xhhFamilyUserService.selectXhhFamilyUserList(xhhFamilyUser);
        ExcelUtil<XhhFamilyUser> util = new ExcelUtil<XhhFamilyUser>(XhhFamilyUser.class);
        util.exportExcel(response, list, "家庭用户关联表数据");
    }

    /**
     * 获取家庭用户关联表详细信息
     */
    @PreAuthorize("@ss.hasPermi('family:FamilyUser:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(xhhFamilyUserService.selectXhhFamilyUserById(id));
    }

    /**
     * 新增家庭用户关联表
     */
    @PreAuthorize("@ss.hasPermi('family:FamilyUser:add')")
    @Log(title = "家庭用户关联表", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody XhhFamilyUser xhhFamilyUser)
    {
        return toAjax(xhhFamilyUserService.insertXhhFamilyUser(xhhFamilyUser));
    }

    /**
     * 修改家庭用户关联表
     */
    @PreAuthorize("@ss.hasPermi('family:FamilyUser:edit')")
    @Log(title = "家庭用户关联表", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody XhhFamilyUser xhhFamilyUser)
    {
        return toAjax(xhhFamilyUserService.updateXhhFamilyUser(xhhFamilyUser));
    }

    /**
     * 删除家庭用户关联表
     */
    @PreAuthorize("@ss.hasPermi('family:FamilyUser:remove')")
    @Log(title = "家庭用户关联表", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(xhhFamilyUserService.deleteXhhFamilyUserByIds(ids));
    }
}

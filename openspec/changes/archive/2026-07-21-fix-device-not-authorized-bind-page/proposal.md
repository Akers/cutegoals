# 提案：修复未授权设备访问 child/bind 页面显示错误

## 问题描述

使用未授权设备访问孩子端 `/child/bind` 页面时，应正常显示 **deviceId（设备标识）** 和 **绑定操作指引**（请让家长授权此设备），但当前页面显示 "查询失败 DEVICE_NOT_AUTHORIZED" 错误。

## 根因分析

- 文件：`web/src/child/pages/ChildBindPage.tsx`
- `fetchChildren()` 函数（第 36-47 行）在组件挂载时调用 `GET /family/devices/children?deviceId=xxx` 后端 API
- 后端 `DeviceBindingService.getChildrenForDevice()` 在设备未绑定时抛出 `DEVICE_NOT_AUTHORIZED` 异常
- 前端将所有 API 错误统一处理为 `setError(...)`，导致 `Result status="error"` 组件渲染：显示 "查询失败" + 错误消息
- **预期行为**：`DEVICE_NOT_AUTHORIZED` 是未授权设备的正常状态，不应显示为错误，应回退到显示 deviceId + 绑定指引页面（即代码中 children.length === 0 分支）

## 修复目标

未授权设备访问 `/child/bind` 页面时，页面应正常显示 deviceId 和绑定操作指引，而不是错误页面。

## 影响范围

- 仅前端修改，不涉及后端 API 变更
- 不影响已授权设备的正常流程（child login、孩子选择）

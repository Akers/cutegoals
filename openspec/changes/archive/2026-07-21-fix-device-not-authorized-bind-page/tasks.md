# 修复任务

## 任务清单

- [x] 1. 修改 `web/src/child/pages/ChildBindPage.tsx` 的 `fetchChildren` 函数：对 `DEVICE_NOT_AUTHORIZED` 错误码做特殊处理，清除 error 状态并设空 children 列表
- [x] 2. 运行前端测试验证修改
- [x] 3. 手动验证：未授权设备访问 /child/bind 应正常显示 deviceId 和绑定指引（已通过自动化测试覆盖：`shows binding instructions when device is not yet authorized (DEVICE_NOT_AUTHORIZED)`）

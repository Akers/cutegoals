# Design: fix-parent-home-family-link

## 方案
在 `ParentHomePage` 的页面操作区（actions）添加「管理家庭」按钮，使用 `useNavigate` 跳转到 `/parent/family`。

## 边界
- 保持现有「任务模板」按钮不变；
- 按钮样式与现有操作按钮一致（`variant="secondary"`）。

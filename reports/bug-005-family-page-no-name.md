# Bug bug-005: 家庭管理页未展示家庭名称、无编辑入口

| Field | Value |
|-------|-------|
| ID | bug-005 |
| Severity | Medium UX |
| Module | parent/family（前端） |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | P-002 |
| Evidence | reports/evidence/P-002/family-no-name-display.png |

## 复现步骤

1. 家长登录（P-001 PASS）
2. 导航到家庭管理页面 `/parent/family`
3. 观察页面内容

## 期望行为

- 页面顶部或主区域展示家庭名称（如「我的家庭」）
- 有编辑入口（如编辑按钮 + 模态框）可修改家庭名称
- 后端 API `GET /api/family` 返回 `{name: "我的家庭", members, children, ...}` 字段完整

## 实际行为

- 页面 **未展示家庭名称**，仅有「邀请家长」「添加孩子」「退出家庭」三个按钮 + 成员列表
- **无编辑家庭名称的入口**
- 后端 API 正常：
  - `GET /api/family` → 200 `{name: "我的家庭", members: [...], children: [...], ...}`
  - `PUT /api/family` with X-CSRF-Token → 200（直接调 API 可成功修改名称）

## 根因分析

- 文件（推测）：`web/src/parent/pages/ParentFamilyPage.tsx`（或类似路径下）
- 前端组件仅 1 行空壳代码，未渲染从后端返回的 `name` 字段
- 当前页面结构：
  - 展示成员列表（已实现）
  - 展示孩子列表（已实现）
  - 家庭名称展示（**缺失**）
  - 家庭名称编辑（**缺失**）
- 后端数据通过 `GET /api/family` 正常返回，但前端 JSX 没有引用 `response.name` 进行渲染

## 修复方向

### 推荐方案 A：在同一页面展示和编辑
在 `/parent/family` 页面顶部添加：
1. **家庭名称展示**：大标题或卡片展示 `data.name`，如 `<h2>{familyData.name}</h2>`
2. **编辑按钮**：铅笔图标按钮，点击弹出编辑模态框
3. **编辑模态框**：含输入框（预填当前名称）+ 保存按钮
4. **保存**：调 `PUT /api/family`，刷新页面显示新名称

### 推荐方案 B：独立设置页
在 `/parent/settings` 或 `/parent/family/edit` 新页面展示家庭名称编辑表单。

### 推荐方案 C：回到最初 plan 的步骤
`PUT /api/family` body 格式如：`{name: "新家庭名称"}`。确认正确 Content-Type（application/json）后实现。

## 影响范围

- **阻塞用例**：P-002（家庭信息查询与更新 FAIL）
- **关联模块**：
  - `web/src/parent/pages/ParentFamilyPage.tsx` — 主页面组件
  - `web/src/parent/components/` — 可能需要新组件（编辑模态框）

## 回归测试要点

1. **单 bug 回归**：导航到 `/parent/family`，确认家庭名称显示在页面上
2. **编辑回归**：点击编辑 → 修改名称 → 保存 → 刷新后确认新名称持久化
3. **并发回归**：确认修改家庭名称不影响成员列表/孩子列表的正常展示

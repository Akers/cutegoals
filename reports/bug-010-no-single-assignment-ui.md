# Bug bug-010: UI 缺单任务分配入口，只有「批量分配」按钮

| Field | Value |
|-------|-------|
| ID | bug-010 |
| Severity | Medium UX |
| Module | parent/tasks（前端） |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | P-010（单任务分配路径） |
| Evidence | reports/evidence/P-010/task-on-0720.png |

## 复现步骤

1. 家长登录 → 导航到任务分配页面 `/parent/tasks`（P-010）
2. 观察页面上的分配按钮

## 期望行为

- 页面上应有 **两个入口**：
  1. **「分配任务」按钮**（单任务分配）：点击弹出模态框，选择模板/孩子/难度/截止日期，提交后创建单条 task_assignment
  2. **「批量分配」按钮**（已有）：用于创建连续多天的重复分配
- 后端 API `POST /api/task-assignments`（单任务）和 `POST /api/task-assignments/batch`（批量）均工作正常

## 实际行为

- 页面上只有 **「批量分配」按钮**，无「分配任务」单任务入口
- 后端 `POST /api/task-assignments` 单任务 API 工作正常（body: `{templateId, childId, difficultyId, deadline}`）
- 用户只能通过批量分配来创建任务，无法精确控制单个任务的分配

## 根因分析

- 文件（推测）：`web/src/parent/pages/tasks/index.tsx`
- 页面组件只渲染了批量分配的功能入口，缺少单任务分配按钮和模态框
- 常见 UI 模式：
  ```tsx
  <Space>
    <Button type="primary" onClick={showSingleAssignModal}>分配任务</Button>
    <Button onClick={showBatchAssignModal}>批量分配</Button>
  </Space>
  ```
  当前只实现了 `showBatchAssignModal`，缺少 `showSingleAssignModal`

## 修复方向

### 推荐方案
1. **添加「分配任务」按钮**，放在「批量分配」按钮旁边
2. **创建单任务分配模态框**，包含：
   - 模板选择（下拉框，加载已启用的模板列表）
   - 孩子选择（下拉框，加载家庭孩子列表）
   - 难度选择（下拉框：DEFAULT / EASY / MEDIUM / HARD）
   - 截止日期选择（DatePicker）
   - 提交按钮 → 调 `POST /api/task-assignments`
3. **成功提示**：`message.success('任务已分配')`，刷新列表

### 替代方案
- 合并到批量分配的模态框中，增加「分配模式」切换（单次/多天）
- 在任务日历视图中增加「点击日期创建任务」的交互

## 影响范围

- **阻塞用例**：P-010（单任务分配路径无法通过 UI 完成）
- **关联模块**：
  - `web/src/parent/pages/tasks/index.tsx` — 主页面组件
  - `web/src/parent/components/TaskAssignModal.tsx` — 可能需要新建的单任务分配模态组件

## 回归测试要点

1. **单 bug 回归**：点击「分配任务」→ 弹出模态框 → 选择模板/孩子 → 提交 → 任务列表中显示新任务
2. **关联回归**：批量分配功能仍正常工作

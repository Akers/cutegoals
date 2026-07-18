# Bug bug-006: 家长端缺少设备管理 UI（授权/查看/解绑）

| Field | Value |
|-------|-------|
| ID | bug-006 |
| Severity | Medium |
| Module | parent/devices（前端） |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | P-006 |
| Evidence | reports/evidence/P-006/child-bind-initial.png, reports/round-1-parent.md（后端 API 测试） |

## 复现步骤

1. 家长登录（P-001 PASS）
2. 在家长端页面导航（检查全部 11 个子页面，包括 `/parent/family`, `/parent/children`, `/parent/settings` 等）
3. 搜索「设备管理」或「设备授权」相关 UI 元素

## 期望行为

- 家长端应有设备管理页面或区域，支持：
  - **查看已绑定设备列表**：展示 deviceId、绑定时间、授权状态
  - **授权新设备**：输入设备 ID，选择关联孩子，提交授权
  - **解绑设备**：对已绑定设备执行解绑操作
- 后端 API 已验证正常：
  - `POST /api/family/devices/bind` → 200（绑定成功，返回一次性 credential）
  - `GET /api/family/devices/children` → 200（返回家庭孩子列表）
  - `DELETE /api/family/devices/{id}` → 200（解绑成功）

## 实际行为

- 家长端 **完全没有设备管理 UI**
- `web/src/parent/pages/` 下 11 个组件均无 device/设备 相关代码
- P-006 数据验证通过 Python urllib 直调后端 API 完成，非通过前端 UI
- 孩子端可通过 `/child/bind` 发起设备绑定流程（拉模式），但家长端没有审批/管理入口

## 根因分析

- 前端代码目录 `web/src/parent/pages/` 缺少设备管理页面组件
- 后端 3 个设备相关 API 全部正常（POST bind, GET children, DELETE）
- 拉模式流程支持：
  1. 孩子在 `/child/bind` 提交设备 ID → 生成 deviceId
  2. 轮询 `GET /api/family/devices/children?deviceId=xxx`
  3. 家长授权 → 孩子选档案 → PIN 登录
- 但家长端缺乏「授权」「查看」「解绑」的 UI 入口

## 修复方向

### 推荐方案 A：在 `/parent/family` 添加设备管理区块
在家庭管理页添加「已授权设备」卡片区域：
1. **设备列表**：表格展示 `device_id`, `status`, `created_at`
2. **授权按钮**：点击弹出输入框（输入设备 ID）+ 选择家庭孩子 → 调 `POST /api/family/devices/bind`
3. **解绑按钮**：每行一个「解绑」按钮，二次确认后调 `DELETE /api/family/devices/{id}`

### 推荐方案 B：新页面 `/parent/devices`
创建独立设备管理页面，侧边栏添加「设备管理」导航项：
1. 页面布局同方案 A 的卡片
2. 侧边栏入口与「家庭」「孩子」「任务」「奖品」并列

### 关键约束
- `device_binding` 表是「设备↔家庭」级别绑定（无 child_id 字段）
- 孩子选择在孩子登录时进行（设备绑定后孩子选档案 → PIN 登录）
- 授权不需要指定 childId，绑定后所有家庭孩子均可使用该设备

## 影响范围

- **阻塞用例**：P-006（设备授权 PARTIAL/FAIL）
- **关联模块**：
  - `web/src/parent/pages/` — 需新增或修改页面组件
  - `server/family/.../DeviceBindingService.java` — 后端 API 正常，不需修改
  - `device_binding` 表结构 — 不需修改

## 回归测试要点

1. **单 bug 回归**：家长端打开设备管理页面 → 可以看到设备列表（初始为空）
2. **授权回归**：输入设备 ID → 授权成功 → 设备出现在列表中（状态 ACTIVE）
3. **解绑回归**：点击解绑 → 确认 → 设备从列表消失 → DB 确认删除

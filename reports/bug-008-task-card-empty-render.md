# Bug bug-008: 任务卡片渲染空白 — 前端 interface 与后端返回字段完全不匹配

| Field | Value |
|-------|-------|
| ID | bug-008 |
| Severity | High |
| Module | parent/tasks（前端） |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | P-010（任务分配列表渲染） |
| Evidence | reports/evidence/P-010/bug-008-task-list-empty.png |

## 复现步骤

1. 家长登录 → 导航到任务分配页面 `/parent/tasks`（P-010）
2. 已通过「批量分配」创建了分配记录（任务分配到 cici）
3. 观察任务卡片渲染效果

## 期望行为

- 任务卡片应展示：
  - **模板名称**（如「整理房间」）
  - **孩子昵称**（如「cici」）
  - **积分值**（如 15 分）
  - **是否逾期**（如「未逾期」或「已逾期」标签）
  - **截止日期**、**状态**等

## 实际行为

- 任务卡片显示「任务列表 [空] · 截止 ... 待处理 积分 [空]」
- 关键字段全部缺失，卡片内容空白

## 根因分析

### 前端 interface 定义（文件：`web/src/parent/pages/index.tsx:86-95`）
```ts
interface TaskAssignment {
  templateTitle: string;     // ❌ 后端实际返回: snapshotTemplateName
  childNickname: string;     // ❌ 后端未返回此字段（需 join 查询）
  points: number;            // ❌ 后端实际返回: snapshotDifficultyReward
  isOverdue: boolean;        // ❌ 后端实际返回: overdue
}
```

### 后端实际返回字段（`GET /api/task-assignments`）
```json
{
  "id": 1,
  "childId": 2,
  "templateId": 2,
  "snapshotTemplateName": "整理房间",
  "snapshotDifficultyName": "DEFAULT",
  "snapshotDifficultyReward": 15,
  "snapshotTemplateDescription": "每天整理自己的房间，保持整洁",
  "snapshotTemplateCategory": "生活习惯",
  "snapshotTemplateTaskType": "DAILY",
  "overdue": false,
  "status": "ASSIGNED",
  "dueDate": "2026-07-18",
  "createdAt": "2026-07-18T..."
}
```

### 字段映射对照表
| 前端期望字段 | 后端实际字段 | 类型匹配 | 备注 |
|-------------|-------------|---------|------|
| `templateTitle` | `snapshotTemplateName` | ❌ 名不匹配 | 渲染时用空 |
| `childNickname` | **不存在** | ❌ 未返回 | 需后端 join child_profile 补充 |
| `points` | `snapshotDifficultyReward` | ❌ 名不匹配 | 渲染时用空 |
| `isOverdue` | `overdue` | ❌ 名不匹配 | 渲染时用 false |

所有 4 个前端渲染依赖的字段全部映射错误，导致卡片无内容。

## 修复方向

### 推荐方案 A（前端修复）
修改前端 interface 定义和渲染逻辑以匹配后端实际返回：

```ts
interface TaskAssignment {
  id: number;
  childId: number;
  templateId: number;
  snapshotTemplateName: string;
  snapshotDifficultyName: string;
  snapshotDifficultyReward: number;
  snapshotTemplateDescription: string;
  snapshotTemplateCategory: string;
  snapshotTemplateTaskType: string;
  overdue: boolean;
  status: string;
  dueDate: string;
  // childNickname 通过后端补充或前端 join
  childNickname?: string;
}
```

渲染时使用：
- `item.snapshotTemplateName` ← 模板名称
- `item.snapshotDifficultyReward` ← 积分
- `item.overdue` ← 是否逾期

### 推荐方案 B（后端补充 childNickname）+ A（前端适配）
后端在 `TaskAssignmentResponse` 中加入 `childNickname` 字段，通过 JOIN `family_member` + `child_profile` 获取：

```sql
SELECT ta.*, cp.nickname AS child_nickname
FROM task_assignment ta
LEFT JOIN child_profile cp ON ta.child_id = cp.id
WHERE ta.family_id = ?
```

### 推荐方案 C（后端加 computed getter）
在后端 DTO 中加 computed getter：
```java
public String getTemplateTitle() { return this.snapshotTemplateName; }
public int getPoints() { return this.snapshotDifficultyReward; }
public boolean getIsOverdue() { return this.overdue; }
```

前端无需修改 interface 字段名。但 childNickname 仍需后端补充。

### 推荐组合
**前端方案 A（主） + 后端补充 childNickname（辅）**：
1. 前端 interface 修改为匹配后端字段名
2. 后端 `TaskAssignmentResponse.java` 加 `childNickname` 字段 + SQL join

## 影响范围

- **阻塞用例**：P-010（任务分配列表页面 FAIL），间接影响 P-011~P-014
- **关联模块**：
  - `web/src/parent/pages/index.tsx` — 前端 interface + 渲染逻辑
  - `server/task/src/main/java/.../dto/TaskAssignmentResponse.java` — DTO 定义（加 childNickname）
  - `server/task/src/main/java/.../service/TaskAssignmentService.java` — SQL join

## 回归测试要点

1. **单 bug 回归**：分配任务后 → 任务卡片展示「整理房间」「cici」「15 分」「未逾期」
2. **关联回归**：任务列表的分页/筛选/排序功能正常
3. **日历视图**：确认日历模式也使用了同样的数据

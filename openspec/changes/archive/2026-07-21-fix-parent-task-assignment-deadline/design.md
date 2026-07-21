# Design: 家长端任务分配 - 重复任务不要求截止日期

## 修复方案

### 方案说明

只改动前端单分配弹窗逻辑。后端 `POST /api/task-assignments/generate-recurring` 已具备正确的重复任务生成能力，无需修改。

### 改动文件

| 文件 | 改动内容 |
|------|---------|
| `web/src/parent/pages/index.tsx` | 单分配弹窗根据 `taskType` 动态切换 UI 和 API 路由 |

### 具体设计

#### 1. 单分配弹窗 UI 动态切换

在 `handleSingleAssign` 函数和单分配弹窗模板中：

- 当所选模板 `taskType === 'REPEAT'` 时：
  - **隐藏**截止日期 `DatePicker`
  - **显示**开始日期和结束日期 `Input[type=date]`
  - 调用 `POST /api/task-assignments/generate-recurring`
  - 请求体：`{ templateId, difficultyId, childId, startDate, endDate }`

- 当所选模板 `taskType === 'LIMITED'` 时：
  - **保持现有行为**：显示截止日期 `DatePicker`，调用 `POST /api/task-assignments`
  - 增加开始日期校验 ≥ 当天

#### 2. 新增状态

```tsx
const [singleStartDate, setSingleStartDate] = useState('');
const [singleEndDate, setSingleEndDate] = useState('');
```

#### 3. 表单验证

- REPEAT：验证 startDate 和 endDate 必填、startDate ≤ endDate
- LIMITED：验证 deadline 必填、startDate ≥ 当天

#### 4. API 路由

```typescript
// REPEAT → 调用 generate-recurring
if (selectedSingleTemplate?.taskType === 'REPEAT') {
  await getClient().post('/task-assignments/generate-recurring', { ... })
} else {
  // LIMITED → 调用单条创建（现有逻辑）
  await getClient().post('/task-assignments', { ... })
}
```

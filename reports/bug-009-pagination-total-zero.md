# Bug bug-009: 分页元数据 bug — `content` 有数据但 `totalElements: 0`

| Field | Value |
|-------|-------|
| ID | bug-009 |
| Severity | Medium |
| Module | task-assignments API + prize API（后端） |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | P-008（分页感知）、P-017（分页感知） |
| Evidence | 后端 API 响应观察 |

## 复现步骤

1. 创建至少一条 task_assignment 记录（P-010 批量分配后）
2. 调 `GET /api/task-assignments?page=0&size=20`
3. 观察响应中的分页元数据
4. 同样操作 `GET /api/prizes?page=0&size=20`

## 期望行为

- 分页响应中当 `content` 有数据时，`totalElements` 应等于 DB 中符合条件的总记录数
- 例如 `content: [item1, item2], totalElements: 2, totalPages: 1`

## 实际行为

- `GET /api/task-assignments` 返回 `content: [items], totalElements: 0`
- `GET /api/prizes` 返回 `content: [items], totalElements: 0`
- 即使 content 有数据，totalElements = 0，totalPages 也因 0/totalElements 计算错误
- `GET /api/blind-boxes` 不受影响（totalElements 正确）

## 根因分析

### 疑似根因
- `TaskAssignmentService.list()` 和 `PrizeService.list()` 在分页查询时，使用了 Page 对象但未正确调用 `page.setTotal()` 或未通过 MyBatis-Plus 的 `selectPage` 方法
- `BlindBoxService.queryPools()` 正确使用 MyBatis-Plus `selectPage` 或手动设置了 total

### 关键代码参考
`BlindBoxService.queryPools` line 248-256（正确写法）：
```java
// 正确：使用 MyBatis-Plus selectPage 自动计算 total
Page<BlindBoxPool> page = new Page<>(pageNum, pageSize);
return blindBoxPoolMapper.selectPage(page, queryWrapper);
```

对比 TaskAssignmentService（错误写法）：
```java
// 疑似：直接 new Page<>() 但未正确绑定 selectPage 结果
Page<TaskAssignment> page = new Page<>(pageNum, pageSize);
List<TaskAssignment> list = taskAssignmentMapper.selectList(queryWrapper);
// ❌ page.setRecords(list) 但未调用 page.setTotal()
// 或：page.getTotal() = 0 因为 selectList 不返回 total
```

### 确认路径
1. 查看 `TaskAssignmentService.list()` 方法实现
2. 查看 `PrizeService.list()` 方法实现
3. 对比 `BlindBoxService.queryPools()` 的正确实现

## 修复方向

### 推荐方案
在 TaskAssignmentService 和 PrizeService 的分页方法中，使用 MyBatis-Plus 的 `selectPage` 替代 `selectList` + 手动 setRecords：

```java
// 修复前（错误）
Page<TaskAssignment> page = new Page<>(pageNum, pageSize);
List<TaskAssignment> list = taskAssignmentMapper.selectList(queryWrapper);
page.setRecords(list);
return page; // totalElements = 0

// 修复后（正确）
Page<TaskAssignment> page = new Page<>(pageNum, pageSize);
page = taskAssignmentMapper.selectPage(page, queryWrapper);
return page; // totalElements = count
```

如果必须使用自定义 SQL，则需手动设置 total：
```java
Page<TaskAssignment> page = new Page<>(pageNum, pageSize);
List<TaskAssignment> list = taskAssignmentMapper.selectMyCustomList(queryWrapper, page);
// 手动计算 total
Long total = taskAssignmentMapper.selectCount(queryWrapper);
page.setTotal(total);
page.setRecords(list);
```

### 注意事项
- 确认 `TaskAssignmentMapper` 是否 extends `MyBatis-Plus BaseMapper<T>`（有 `selectPage` 方法）
- 如果使用自定义 XML Mapper，需在 XML 中同时提供 count 查询

## 影响范围

- **阻塞用例**：P-008（模板分页）、P-017（奖品分页）— 前端感知分页异常
- **关联模块**：
  - `server/task/src/main/java/.../service/TaskAssignmentService.java`
  - `server/prize/src/main/java/.../service/PrizeService.java`
  - `server/blind-box/.../service/BlindBoxService.java`（参考正确实现）

## 回归测试要点

1. **单 bug 回归**：有数据时分页响应 `totalElements > 0`
2. **边界回归**：0 条记录时 `totalElements = 0, content = []`
3. **关联回归**：确认盲盒池分页仍然正确（不受改动影响）

# Bug bug-012: points_balance 记录永不创建（无 insert 方法），全积分链路断开

| Field | Value |
|-------|-------|
| ID | bug-012 |
| Severity | **Critical** |
| Module | points/balance（PointsBalanceMapper + ChildProfileService） |
| Status | open |
| Discovered | 2026-07-18 |
| Blocking Cases | P-013~P-016, P-019, C-004~C-007, X-001 |
| Evidence | reports/backend.log:1909~2031（PointsBalanceMapper.findByChildId 多次返回 Total: 0）|

## 复现步骤

1. P-004 创建孩子档案 cici（PASS，child_profile 表有记录 id=2）
2. 没有任何积分操作时查询 points_balance：`SELECT * FROM points_balance WHERE child_id = 2` → 空
3. 尝试审核通过任务（P-013 如果可执行），或查询积分余额（P-015）
4. 观察后端日志，确认 `POINTS_ACCOUNT_NOT_FOUND` 异常

## 期望行为

- 创建孩子档案（P-004）时，应自动创建对应的 `points_balance` 记录（balance=0, total_earned=0）
- 后续积分操作（审核通过、积分调整、兑换扣减）应在该记录上进行

## 实际行为

- `points_balance` 表无 child_id=2 的记录
- 后端日志显示 7 次 `SELECT * FROM points_balance WHERE child_id = ?` 返回 `Total: 0`
- `TaskReviewService.approveAttempt` 和 `PointsService` 各方法抛出 `POINTS_ACCOUNT_NOT_FOUND` 业务异常

### 后端日志确认
```
07:19:15.829 — SELECT * FROM points_balance WHERE child_id = 2 → Total: 0
07:19:15.830 — Business exception: code=POINTS_ACCOUNT_NOT_FOUND, message=Points account not found for child: 2
07:19:15.844 — SELECT * FROM points_balance WHERE child_id = 2 → Total: 0 (重复3次)
07:19:27.175 — SELECT * FROM points_balance WHERE child_id = 2, child_id=2 → Total: 0 ×5
07:19:27.203 — SELECT * FROM points_balance WHERE child_id = 2 FOR UPDATE → Total: 0
```

## 根因分析

### PointsBalanceMapper 缺少 insert 方法
- 文件：`server/points/src/main/java/com/cutegoals/points/mapper/PointsBalanceMapper.java`
- **只有 `selectByChildId` 和 `updateBalanceWithVersion` 两个方法**
- **完全没有 insert 方法**

### ChildProfileService.createProfile 不创建 points_balance
- `ChildProfileService.createProfile` 在 DB 中插入 `child_profile` 记录
- **没有调用任何 `pointsBalanceMapper.insert`**
- 代码全局 grep `new PointsBalance(` 无结果 → 没有地方创建 PointsBalance 对象

### 无 DB 触发器补偿
- DB 侧无自动创建 `points_balance` 的 trigger
- `TaskAttempt` 的 `afterInsert` 或其他事件钩子也未创建
- 确认 Migration 脚本中无 `points_balance` 的默认行 INSERT

### TaskReviewService 的失败路径
`TaskReviewService.approveAttempt` line 489-491：
```java
PointsBalance balance = pointsBalanceMapper
    .findByChildIdForUpdate(attempt.getChildId())
    .orElseThrow(() -> new BusinessException(POINTS_ACCOUNT_NOT_FOUND));
```
由于 `findByChildIdForUpdate` 返回 empty Optional，直接抛异常，无法继续审核。

## 修复方向

### 推荐方案：组合方案 A + C

**方案 A（新孩子）：ChildProfileService.createProfile 联动创建 points_balance**
```java
// 在 ChildProfileService.createProfile 末尾
public ChildProfile createProfile(CreateChildRequest request) {
    // ... 现有逻辑创建 child_profile

    // 新增：同时创建 points_balance
    PointsBalance balance = new PointsBalance();
    balance.setChildId(profile.getId());
    balance.setBalance(0);
    balance.setTotalEarned(0);
    balance.setVersion(0);
    pointsBalanceMapper.insert(balance);

    return profile;
}
```

**方案 B（并发安全，不推荐）**：在首次访问时 lazy init
```java
// 在 PointsBalanceMapper.findByChildId 查询为空时自动创建
// 并发风险：两个线程同时查询到 0，都尝试 insert，可能唯一键冲突
```

**方案 C（老孩子）：Migration 脚本补建记录**
创建新 migration `V*__create_missing_points_balance.sql`：
```sql
INSERT INTO points_balance (child_id, balance, total_earned, version)
SELECT cp.id, 0, 0, 0
FROM child_profile cp
LEFT JOIN points_balance pb ON cp.id = pb.child_id
WHERE pb.id IS NULL;
```

**方案 D：PointsBalanceMapper 加 insert 方法**
```java
// PointsBalanceMapper.java
int insert(PointsBalance balance);
```
对应 MyBatis XML（或 MyBatis-Plus 自动生成）。

### 推荐组合
1. **PointsBalanceMapper.java** 加 `int insert(PointsBalance balance)` 方法
2. **ChildProfileService.createProfile** 末尾调用 `pointsBalanceMapper.insert()` 创建 points_balance 记录
3. **Migration 脚本** 为已存在的 child_profile 补建 points_balance 记录
4. **确认** `TaskReviewService` 中 `findByChildIdForUpdate().orElseThrow(...)` 的调用路径不再触发 POINTS_ACCOUNT_NOT_FOUND

## 影响范围

- **阻塞用例**：
  - P-013（审核通过 — 积分入账失败）
  - P-014（审核驳回 — 同上）
  - P-015（查询积分余额 — points_balance 为空）
  - P-016（积分调整 — 同上）
  - P-019（核销幂等 — 依赖积分扣减）
  - C-004~C-007（孩子端 4 个积分相关用例）
  - X-001（完整工作流）
- **关联模块**：
  - `server/points/.../mapper/PointsBalanceMapper.java` — 加 insert 方法
  - `server/points/.../entity/PointsBalance.java` — 可能需确认实体字段
  - `server/family/.../service/ChildProfileService.java` — createProfile 联动
  - `server/points/.../service/TaskReviewService.java:489-491` — 失败路径
  - `db/migration/V*__*.sql` — 补建记录 migration

## 回归测试要点

1. **单 bug 回归**：
   - 创建孩子（P-004）后直接查询 `points_balance`，确认存在 balance=0 的记录
   - 测试 `findByChildIdForUpdate` 不抛 POINTS_ACCOUNT_NOT_FOUND
2. **关联回归**：
   - 审核通过任务（P-013）确认积分增加
   - 积分调整（P-016）确认余额更新
   - 兑换扣减（C-006）确认余额减少
3. **Migration 回归**：DB 重置后执行 migration，确认 migration 脚本不影响新创建的孩子的 points_balance（使用 `ON CONFLICT DO NOTHING` 或 `LEFT JOIN ... WHERE pb.id IS NULL`）

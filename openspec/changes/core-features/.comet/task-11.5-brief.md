# Task 11.5: E2E Integration Tests for Task Type System

## 目标

创建端到端集成测试，覆盖 LIMITED/REPEAT/STANDING 三类型的完整功能。这些测试应验证现有实现，不应发现新 bug（如果失败，说明有回归）。

## 依赖

所有 11.1-11.4 任务已完成：
- 11.1：task_type/type_config 数据库列 + 错误码
- 11.2：frequency+trigger_day 模型（TaskTemplateFrequencyService）
- 11.3：STANDING（submission_count）+ LIMITED（start_date/end_date 状态机）业务逻辑
- 11.4：REPEAT 双触发器（RepeatTaskScheduler + 审核提交钩子）

## 要求

在 `server/web/src/test/java/com/cutegoals/web/it/` 下创建 `TaskTypeIntegrationTest.java`，继承 `WebIntegrationTestBase`。

### 测试场景

#### 1. LIMITED 任务全生命周期
- 创建 LIMITED 模板（task_type=LIMITED, type_config 含 start_date/end_date）
- 模板查询确认字段正确
- 批量分配给孩子
- 标记类型不可修改：PUT 改 task_type 返回 `TASK_TEMPLATE_TYPE_IMMUTABLE`
- 非 deadline 内提交返回 `TASK_LIMITED_NOT_STARTED`/`TASK_LIMITED_EXPIRED`

#### 2. STANDING 任务全生命周期  
- 创建 STANDING 模板（task_type=STANDING, type_config 含 max_submissions）
- 为多个孩子批量分配
- 提交并批准，确认 submission_count 自增
- 达上限后提交返回 `TASK_STANDING_LIMIT_REACHED`
- 再次批准不应增加 submission_count（已到达上限）

#### 3. REPEAT 任务 API 覆盖
- 创建 REPEAT 模板（task_type=REPEAT, type_config 含 frequency/trigger_day）
- 分配后确认首期 PENDING_OPEN/OPEN（视触发日而定）
- 非触发日提交返回 `TASK_REPEAT_NOT_TRIGGER_DAY`
- 审核通过后产生下一期

#### 4. 错误码与边界
- 各类错误码的 HTTP 状态码语义正确（400/403/409）

### 约束

- 使用 `WebIntegrationTestBase` 提供的 `MockMvc`、`postJsonAuth()`、`getAuth()`、`assertStatus()`
- 不需要初始化整个系统（如果已有 AuthFamilyIntegrationTest 类似的准备步骤，可复用）
- 测试数据在 `@BeforeEach` 中准备（login → 获取 session → 创建模板）
- 每个测试方法独立，互不影响
- 继承测试基类的 `@SpringBootTest` 配置

### 参考

- `server/web/src/test/java/com/cutegoals/web/it/TaskPointsIntegrationTest.java`
- `server/web/src/test/java/com/cutegoals/web/it/AuthFamilyIntegrationTest.java`

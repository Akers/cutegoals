# Tasks: 修复重复任务截止日期

- [x] 1. **复现问题**：编写单元测试，验证 REPEAT 任务分配的截止日期未按频率模式计算
- [x] 2. **注入依赖**：在 `TaskAssignmentService` 中添加 `TaskTemplateFrequencyService` 字段
- [x] 3. **修复截止日期逻辑**：修改 `createAssignment()` 中 REPEAT 分支（第 91-93 行），调用 `nextTriggerDate()` 计算截止日期
- [x] 4. **验证测试通过**：运行新增的测试和项目已有测试（27/27 通过）
- [x] 5. **提交修复**

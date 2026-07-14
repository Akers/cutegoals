## 修复任务

- [x] 1. 在 `TaskTemplate` 实体 `typeConfig` 字段添加 `JacksonTypeHandler` 类型处理器，并验证 `TaskTemplateServiceTest` 通过。
- [x] 2. 在 `TaskAssignment` 实体 `snapshotTemplateTypeConfig` 字段添加 `JacksonTypeHandler` 类型处理器，并验证 `TaskAssignmentServiceTest` 通过。
- [x] 3. 运行 `mvn -pl :task,:task-review,:common -am test` 全量回归，确认无失败。
- [x] 4. 验证 `FlywayMigrationTest` 仍通过，确认 V12 迁移无回归。
- [x] 5. 提交修复并进入 verify。

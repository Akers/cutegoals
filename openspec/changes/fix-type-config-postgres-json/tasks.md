## 修复任务

- [x] 1. 实现 `JsonTypeHandler`：检测 PostgreSQL 时使用 `PGobject` 绑定 JSON 列，其他数据库回退到 `setString`；common 模块不引入 postgresql 编译依赖。
- [x] 2. 将 `TaskTemplate` 实体 `typeConfig` 字段的类型处理器从 `JacksonTypeHandler` 替换为 `JsonTypeHandler`。
- [x] 3. 将 `TaskAssignment` 实体 `snapshotTemplateTypeConfig` 字段的类型处理器从 `JacksonTypeHandler` 替换为 `JsonTypeHandler`。
- [x] 4. 为 `JsonTypeHandler` 添加单元测试，覆盖 H2 路径、`setNull` 路径以及通过测试桩模拟的 PostgreSQL 路径。
- [x] 5. 运行 `mvn -pl :task,:task-review,:common -am test` 全量回归，确认无失败。
- [x] 6. 更新验证报告并重新进入 verify。

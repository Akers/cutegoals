# tasks: 修复 Flyway 迁移 V13 验证失败

## 任务清单

- [x] 1. 在 `server/web/src/main/resources/application.yml` 添加 `spring.flyway.out-of-order: true`
- [x] 2. 在 `server/web/src/test/resources/application-test.yml` 同步添加 `spring.flyway.out-of-order: true`
- [x] 3. 启动后端验证修复：`./scripts/start-dev.sh --use-env`
- [x] 4. 修复 `FlywayMigrationTest`：断言从 13 更新为 15 个迁移，添加 V13/V15 版本验证

# design: 修复 Flyway 乱序迁移验证失败

## 修复方案

在 `server/web/src/main/resources/application.yml` 的 `spring.flyway` 配置中添加 `out-of-order: true`。

### 修改位置

`server/web/src/main/resources/application.yml` 第 28-31 行：

```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    out-of-order: true      # 新增：允许乱序迁移
```

### 同步更新测试配置

`server/web/src/test/resources/application-test.yml` 第 19-23 行同样添加 `out-of-order: true`，确保集成测试配置一致。

## 风险评估

- **低风险**：Flyway `out-of-order` 是官方支持的标准配置，仅影响迁移执行顺序检查
- 各迁移文件的 SQL 是幂等或独立操作，乱序执行不会产生数据一致性问题
- V13 给 `prize` 表添加列，V14/V15 操作的是其他表（submission、session），不冲突

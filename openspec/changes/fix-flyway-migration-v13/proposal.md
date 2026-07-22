# proposal: 修复 Flyway 迁移 V13 验证失败

## 问题描述

使用 `./scripts/start-dev.sh --use-env` 启动后端时，Spring Boot 启动失败，Flyway 迁移验证报错：

```
Validate failed: Migrations have failed validation
Detected resolved migration not applied to database: 13.
```

应用上下文初始化失败，所有 bean（`flywayInitializer` → `refreshTokenMapper` → `tokenService` → `webSecurityConfig`）因此级联报错。

## 根因分析

- 项目中存在 Flyway 迁移文件 V13 (`V13__add_prize_model_config.sql`)，用于给 `prize` 表添加 `prize_type`、`prize_category`、`type_config` 等列
- 同时存在 V14 (`V14__add_resubmission_controls.sql`) 和 V15 (`V15__add_child_session_support.sql`)
- 数据库可能已先应用了 V14、V15（来自其他分支或基线），而 V13 是后来新增的
- Flyway 默认配置 `out-of-order: false`，禁止乱序迁移，因此验证阶段报错

## 修复目标

在 Flyway 配置中启用 `out-of-order: true`，允许 Flyway 对尚未应用的迁移按任意顺序执行。

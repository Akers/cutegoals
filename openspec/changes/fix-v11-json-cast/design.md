## Context

`fix-v11-postgresql-instr` hotfix 将 `V11` 中的 `INSTR()` 替换为 `POSITION()` 后，PostgreSQL 仍报 `column "type_config" is of type json but expression is of type text`。这是因为 `type_config` 列在 V10 中被定义为 `JSON` 类型，而 `SET type_config = '...'` 把文本字符串赋给 JSON 列，PostgreSQL 拒绝隐式转换。

## Goals / Non-Goals

**Goals：**
- 用 `CAST('...' AS JSON)` 包装 V11 中所有 `type_config` 赋值，确保 PostgreSQL 接受
- 保持 H2 测试环境兼容性（`CAST(... AS JSON)` 在 H2 中已验证可用）
- 保持数据语义不变

**Non-Goals：**
- 不修改 `type_config` JSON 结构
- 不修改 V10 的列定义
- 不引入新 capability 或接口

## Decisions

**决策 1：使用 `CAST(expression AS JSON)` 而非 `::json`**

- `CAST` 是 ANSI SQL 标准，H2 与 PostgreSQL 均支持。
- `::json` 是 PostgreSQL 特有语法，H2 在默认模式下可能不接受。

## Risks / Trade-offs

- [Risk] 已尝试应用 V11 但失败的 PostgreSQL 实例，其 `flyway_schema_history` 可能已记录失败状态，需要用户手动清理后重试。→ 修复后的脚本本身幂等且可重复执行。

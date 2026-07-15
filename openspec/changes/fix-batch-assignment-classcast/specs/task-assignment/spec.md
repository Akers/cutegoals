## MODIFIED Requirements

### Requirement: 原子批量分配

#### Scenario: 批量请求中的 childIds 为 JSON 整数数组
- **WHEN** 家长提交批量分配请求，childIds 数组元素为 JSON 整数（如 `[1]`）
- **THEN** 系统正确解析为长整数，不抛出 `ClassCastException`，并正常创建分配

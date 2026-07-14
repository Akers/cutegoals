## 1. 修复 V11 迁移 PostgreSQL 兼容性

- [ ] 1.1 将 `V11__add_frequency_to_type_config.sql` 中的 `INSTR()` 替换为 `POSITION()`，确保 PostgreSQL 与 H2 兼容。
- [ ] 1.2 运行 `mvn -f server/pom.xml test -DskipITs` 验证所有后端测试通过。

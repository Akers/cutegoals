## 1. 修复 V11 JSON cast

- [ ] 1.1 将 `V11__add_frequency_to_type_config.sql` 中 4 处 `SET type_config = '...'` 改为 `SET type_config = CAST('...' AS JSON)`；验证 PostgreSQL 与 H2 兼容。
- [ ] 1.2 运行 `mvn -f server/pom.xml test -DskipITs` 验证所有后端测试通过。

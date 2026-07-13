# Tasks: fix-family-member-lookup

- [x] 1.1 在 FamilyMemberMapper 中新增 findByAccountId(Long accountId) 方法，使用 SELECT * FROM family_member WHERE account_id = #{accountId} LIMIT 1。
- [x] 1.2 修改 WebSecurityConfig，使用 familyMemberMapper.findByAccountId(claims.accountId()) 替代 findByAccountIdAndRole(...) 设置 ATTR_FAMILY_ID。
- [x] 1.3 验证后端编译：mvn -pl :web -am compile exit 0。
- [x] 2.1 验证后端测试：mvn -pl :web -am test 全绿（66 tests）。
- [x] 2.2 验证前端类型检查：cd web && npx tsc -b 0 errors。
- [x] 2.3 验证前端测试：cd web && npm test 与 baseline 一致（14 failed / 65 passed），0 新增失败。
- [x] 2.4 git commit：fix(auth): look up family by account id regardless of role order。
- [x] 2.5 运行 build guard 推进到 verify。

# Verification Report: fix-family-member-lookup

## 检查项与结果

| 检查项 | 状态 | 说明 |
|--------|------|------|
| tasks.md 全部完成 | PASS | 8/8 任务已勾选 [x] |
| 改动文件与 tasks 一致 | PASS | FamilyMemberMapper.java、WebSecurityConfig.java |
| 后端编译 | PASS | mvn -pl :web -am compile BUILD SUCCESS |
| 后端测试 | PASS | mvn -pl :web -am test 66 tests 全绿 |
| 前端类型检查 | PASS | npx tsc -b 0 errors |
| 前端测试 | PASS | npm test 与 baseline 一致 14 failed / 65 passed |
| 安全问题 | PASS | 无硬编码密钥、无 unsafe 操作 |
| 代码审查 | SKIP | review_mode: off |

## 根因修复确认

上一个 hotfix 使用 findByAccountIdAndRole(accountId, roles.get(0)) 获取 familyId，当 token 中第一个角色不是家庭角色（如 INSTANCE_ADMIN）时返回空，导致 familyId 未设置。
本次修复：
1. 新增 FamilyMemberMapper.findByAccountId(accountId)，按账号 ID 直接查询家庭成员。
2. WebSecurityConfig 改用 findByAccountId(accountId) 设置 ATTR_FAMILY_ID，不受角色顺序影响。

## 结论

无 CRITICAL / IMPORTANT 问题。验证通过。

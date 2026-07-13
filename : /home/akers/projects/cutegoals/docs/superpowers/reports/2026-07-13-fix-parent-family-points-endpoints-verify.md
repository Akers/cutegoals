: # Verification Report: fix-parent-family-points-endpoints

## 检查项与结果

| 检查项 | 状态 | 说明 |
|--------|------|------|
| tasks.md 全部完成 | PASS | 9/9 任务已勾选 `[x]` |
| 改动文件与 tasks 一致 | PASS | 涉及 `server/web/src/main/java/com/cutegoals/web/config/WebSecurityConfig.java` 与 `web/src/parent/pages/index.tsx` |
| 后端编译 | PASS | `mvn -pl :web -am compile` BUILD SUCCESS |
| 后端测试 | PASS | `mvn -pl :web -am test`：66 tests 全绿（AuthFamilyIntegrationTest 20、ExchangePointsIntegrationTest 24、TaskPointsIntegrationTest 19、AuthControllerTest 3） |
| 前端类型检查 | PASS | `npx tsc -b` 0 errors |
| 前端测试 | PASS | `npm test -- --run` 与 baseline 一致：14 failed / 65 passed，0 新增失败 |
| 安全问题 | PASS | 无硬编码密钥、无新增 unsafe 操作；新增 familyId 查询使用已有 `FamilyMemberMapper` 与 MyBatis-Plus |
| 代码审查 | SKIP | `review_mode: off`（hotfix 直接模式），已在流程中记录 |

## 根因修复确认

1. **家庭菜单 `RESOURCE_NOT_FOUND`**：原因为 `WebSecurityConfig.jwtAuthenticationFilter` 解析 token 后未设置 `ATTR_FAMILY_ID`。修复后，使用 `FamilyMemberMapper.findByAccountIdAndRole(accountId, role)` 查询账号所属家庭并写入请求属性，`
FamilyController.getFamily` 可正确获取 `familyId`。
2. **积分菜单 `No static resource api`（404）**：原因为 `ParentPointsPage` 在 `selectedChild` 为空字符串时调用 `useApi('')`，请求落到 `/api`。修复后，空 child 时传入 `skip: !selectedChild`，不发起请求。

## 结论

无 CRITICAL / IMPORTANT 问题。验证通过，可进入 archive 阶段。

# 修复任务清单

## 后端修改

### 1. 扩展 JwtClaims 和 TokenService

- [x] **Java**: 在 `TokenService.java` 的 `JwtClaims` record 中新增 `childId` 和 `familyId` 可选字段
- [x] **Java**: 新增 5 参数 `generateAccessToken(accountId, roles, sessionId, childId, familyId)` 重载方法
- [x] **Java**: 修改 `parseAccessToken()` 方法，从 claims 中提取 `childId`/`familyId`

**文件**: `server/auth/src/main/java/com/cutegoals/auth/service/TokenService.java`

### 2. 修改孩子登录端点设置 JWT Cookie

- [x] **Java**: `DeviceController.childLogin()` 增加 `HttpServletResponse response` 参数和 `TokenService`/`TokenCookieWriter` 注入
- [x] **Java**: 登录成功后调用 `generateAccessToken()` + `setTokenCookies()` + `setCsrfCookie()`

**文件**: `server/family/src/main/java/com/cutegoals/family/controller/DeviceController.java`

### 3. 修改 JWT Filter 提取 childId

- [x] **Java**: `jwtAuthenticationFilter()` 中从 claims 提取 `childId` 并设置 `ATTR_CHILD_ID`
- [x] **Java**: 从 claims 提取 `familyId` 并设置 `ATTR_FAMILY_ID`（如尚未设置）
- [x] **Java**: 在 `AuthConstants` 中添加 `ATTR_CHILD_ID` 常量

**文件**: `server/web/src/main/java/com/cutegoals/web/config/WebSecurityConfig.java`
**文件**: `server/common/src/main/java/com/cutegoals/common/constant/AuthConstants.java`

### 4. 修改 /auth/me 返回 childId

- [x] **Java**: `AuthController.me()` 响应增加 `childId` 和 `familyId` 字段
- [x] **Java**: `AuthController.refresh()` 对 child session 使用 5 参数 `generateAccessToken`

**文件**: `server/web/src/main/java/com/cutegoals/web/controller/AuthController.java`

## 前端修改

### 5. AuthContext 适配

- [x] **TypeScript**: `AuthContext.tsx` 中 `/auth/me` 响应处理增加 `childId` 提取

**文件**: `web/src/shared/auth/AuthContext.tsx`

## 验证（见 verify 阶段）

验证任务已移至 verify 阶段执行：
- 孩子登录后 `/auth/me` 返回正确的 `childId` 和 `familyId`
- 孩子登录后页面刷新不会跳转到选择档案页
- 菜单点击正常跳转到对应页面

# 修复方案：孩子端登录会话持久化

## 方案概述

在孩子登录时生成 JWT token 并写入 HttpOnly Cookie，扩展 JWT claims 以支持 childId，使孩子端获得与家长端一致的持久会话能力。

## 详细方案

### 1. 扩展 JwtClaims 和 TokenService

**文件**: `server/auth/src/main/java/com/cutegoals/auth/service/TokenService.java`

- `JwtClaims` record 新增 `childId`（`Long`，可选）和 `familyId`（`Long`，可选）字段
- 新增 `generateChildAccessToken(Long childId, Long familyId, List<String> roles, String sessionId)` 方法，生成包含 `childId` 和 `familyId` 的 JWT
- `parseAccessToken()` 方法从 JWT payload 提取 `childId` 和 `familyId` 字段

JWT claims 新结构：
```
{
  "sub": "accountId 或 -childId",
  "roles": ["CHILD"],
  "sid": "session-uuid",
  "cid": 123,       // 新增: childId
  "fid": 456        // 新增: familyId
}
```

### 2. 修改孩子登录端点

**文件**: `server/family/src/main/java/com/cutegoals/family/controller/DeviceController.java`

`childLogin()` 方法：
- 方法签名增加 `HttpServletResponse response` 参数
- 注入 `TokenService` 和 `TokenCookieWriter`
- 验证通过后：调用 `tokenService.generateChildAccessToken(childId, familyId, ["CHILD"], sessionId)` 生成 access token
- 调用 `tokenCookieWriter.setTokenCookies(response, accessToken, refreshToken)` 设置 cookie

### 3. 修改 JWT Filter 以支持 childId

**文件**: `server/web/src/main/java/com/cutegoals/web/config/WebSecurityConfig.java`

`jwtAuthenticationFilter()` 中：
- 从 claims 提取 `childId` 并设置 `ATTR_CHILD_ID` 请求属性
- 从 claims 提取 `familyId` 并设置 `ATTR_FAMILY_ID` 请求属性

### 4. 修改 /auth/me 端点

**文件**: `server/web/src/main/java/com/cutegoals/web/controller/AuthController.java`

`me()` 方法：
- 从 request attribute 或 claims 获取 `childId`，放入响应
- 从 request attribute 获取 `familyId`，放入响应

### 5. 前端 AuthContext 适配

**文件**: `web/src/shared/auth/AuthContext.tsx`

- `/auth/me` 响应处理增加 `childId` 提取
- `Account` 接口已有 `childId` 字段，无需修改接口定义

## 不修改的内容

- 前端 `ChildLoginPage` 和 `ChildBindPage` 无需修改（登录成功后通过 context 获取 childId）
- 路由配置 (`routes.ts`) 无需修改（AuthGuard 逻辑不变）
- 无需新增 spec（此修复不改变现有 capability 的验收场景）

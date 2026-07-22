# 修复孩子端登录会话持久化

## 问题描述

孩子端（`/child/bind`）完成 PIN 登录后，所有菜单点击（首页、我的任务、奖品商店等）均跳转到“选择档案”页面，无法以该孩子身份正常使用。期望行为是：首次选择档案并输入 PIN 后，应以该孩子身份完成登录，后续菜单操作不再重复要求选择档案。

## 复现步骤

1. 打开孩子端 `/child/bind`，选择一个孩子档案
2. 跳转到 `/child/login`，输入正确 PIN 码
3. 登录成功后进入 `/child` 首页
4. 点击任意菜单（如“我的任务”）→ 页面刷新 → 被重定向到 `/child/bind`（选择档案页）

## 根因分析

孩子登录的会话机制与家长登录不一致，导致页面刷新后无法恢复会话：

### 问题 1：孩子登录不生成 JWT Token

**`DeviceController.childLogin()`**（`/api/auth/child/login`）只返回 `sessionId` 等 JSON 数据，**不调用 `TokenCookieWriter.setTokenCookies()`** 设置 JWT cookie。

对比家长登录（`AuthController.login()`）：验证通过后生成 `accessToken` + `refreshToken`，通过 HttpOnly Cookie 写入浏览器。

### 问题 2：`/auth/me` 不返回 childId

`AuthController.me()` 只从 JWT claims 中提取 `accountId`、`roles`，缺少年龄端需要的 `childId` 和 `familyId`。

### 问题 3：JWT Claims 缺少 childId

`TokenService.generateAccessToken()` 生成的 JWT claims 只包含 `accountId`、`roles`、`sessionId`。孩子端登录的 `accountId` 为 null，导致解析后无法获取孩子身份。

### 问题 4：前端 AuthContext 不恢复 childId

`AuthContext` 中 `/auth/me` 响应处理后不提取 `childId`，刷新页面后 account 状态缺少孩子身份信息。

### 触发链

```
页面刷新 /child/tasks
→ AuthGuard 检查 isAuthenticated → false（/auth/me 无 JWT → 401）
→ AuthGuard 重定向到 /child/login
→ ChildLoginPage 无 childId URL 参数
→ 重定向到 /child/bind（选择档案页）
```

## 修复目标

1. **孩子登录生成 JWT token**：`/api/auth/child/login` 返回时设置 `access_token` 和 `refresh_token` cookie
2. **JWT claims 扩展**：支持 `childId` 和 `familyId` 字段，使孩子身份的 claims 可被正确解析
3. **`/auth/me` 返回 childId**：使前端刷新后能恢复孩子的完整身份信息
4. **前端 AuthContext 同步**：从 `/auth/me` 响应中提取并存储 `childId`

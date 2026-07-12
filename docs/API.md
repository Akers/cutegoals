# CuteGoals 2.0 — 后端 API 接口说明

本文档列出 CuteGoals 2.0 后端提供的 RESTful API 接口。所有接口返回统一格式：

```json
{
  "data": { ... },
  "requestId": "xxxxxxxxxxxxxxxx",
  "error": null
}
```

出错时返回：

```json
{
  "data": null,
  "requestId": "xxxxxxxxxxxxxxxx",
  "error": {
    "error_code": "ERROR_CODE",
    "message": "错误描述"
  }
}
```

> 认证接口通过 `HttpOnly` Cookie 下发访问令牌，前端需通过 `X-XSRF-Token` / `X-CSRF-TOKEN` 请求头回传 CSRF 令牌。详见 [AuthController](server/web/src/main/java/com/cutegoals/web/controller/AuthController.java)。

## 目录

- [认证与初始化](#认证与初始化)
- [家庭管理](#家庭管理)
- [任务管理](#任务管理)
- [积分管理](#积分管理)
- [奖品与兑换](#奖品与兑换)
- [管理后台](#管理后台)
- [健康检查](#健康检查)

## 认证与初始化

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/initialize` | 首次部署初始化，创建首位管理员 |
| POST | `/api/auth/login` | 手机号+密码登录 |
| POST | `/api/auth/child/login` | 孩子 PIN 登录 |
| POST | `/api/auth/refresh` | 刷新访问令牌 |
| POST | `/api/auth/logout` | 登出并撤销会话 |
| PUT | `/api/auth/password` | 修改密码 |
| POST | `/api/auth/sms/send` | 发送短信验证码（需配置短信服务） |
| POST | `/api/auth/sms/login` | 短信验证码登录（需配置短信服务） |
| POST | `/api/auth/recover/initiate` | 发起管理员恢复流程 |
| POST | `/api/auth/recover` | 执行管理员恢复 |

### 初始化

```bash
POST /api/auth/initialize
Content-Type: application/json

{
  "token": "INIT_TOKEN",
  "phone": "13800138000",
  "password": "管理员密码"
}
```

### 登录

```bash
POST /api/auth/login
Content-Type: application/json

{
  "phone": "13800138000",
  "password": "用户密码"
}
```

## 家庭管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/family` | 获取当前家庭信息 |
| PUT | `/api/family` | 更新家庭信息 |
| DELETE | `/api/family/members/{id}` | 移除家庭成员 |
| POST | `/api/family/members/me/leave` | 当前家长退出家庭 |
| GET | `/api/family/export` | 导出家庭数据 |
| POST | `/api/family/children` | 创建孩子档案 |
| PUT | `/api/family/children/{id}` | 更新孩子档案 |
| DELETE | `/api/family/children/{id}` | 删除（匿名化）孩子档案 |
| POST | `/api/family/invitations` | 邀请其他家长加入 |
| POST | `/api/family/invitations/{id}/accept` | 接受邀请 |
| POST | `/api/family/invitations/{id}/reject` | 拒绝邀请 |
| POST | `/api/family/invitations/{id}/revoke` | 撤销邀请 |
| POST | `/api/family/devices/bind` | 绑定设备 |
| DELETE | `/api/family/devices/{id}` | 解绑设备 |
| GET | `/api/family/devices/children` | 获取孩子设备列表 |

## 任务管理

### 任务模板

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/task-templates` | 创建任务模板 |
| PUT | `/api/task-templates/{id}` | 更新任务模板 |
| GET | `/api/task-templates` | 查询任务模板列表 |
| GET | `/api/task-templates/{id}` | 获取任务模板详情 |
| DELETE | `/api/task-templates/{id}` | 软删除任务模板 |
| PUT | `/api/task-templates/{id}/enabled` | 启用/禁用任务模板 |

查询参数：`page`、`pageSize`、`category`、`enabled`、`includeDeleted`、`keyword`。

### 任务分配

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/task-assignments` | 分配任务 |
| POST | `/api/task-assignments/batch` | 批量分配任务 |
| POST | `/api/task-assignments/generate-recurring` | 生成周期任务 |
| GET | `/api/task-assignments` | 查询任务分配列表 |
| GET | `/api/task-assignments/calendar` | 日历视图任务 |
| GET | `/api/task-assignments/{id}` | 获取任务分配详情 |
| POST | `/api/task-assignments/{id}/cancel` | 取消任务分配 |
| PUT | `/api/task-assignments/{id}` | 更新任务分配 |
| PUT | `/api/task-assignments/{id}/late-policy` | 更新逾期策略 |

### 任务提交与审核

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/task-review/submissions` | 提交任务完成 |
| POST | `/api/task-review/{attemptId}/reject` | 拒绝任务提交 |
| POST | `/api/task-review/{attemptId}/approve` | 批准任务提交 |
| GET | `/api/task-review/pending` | 待审核列表 |
| GET | `/api/task-review/history` | 审核历史 |
| GET | `/api/task-review/child/{childId}/history` | 指定孩子的审核历史 |

## 积分管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/points/balance/{childId}` | 查询孩子积分余额 |
| POST | `/api/points/adjustments` | 积分调整（家长/管理员） |
| GET | `/api/points/ledger/{childId}` | 查询孩子积分流水 |
| GET | `/api/points/summary` | 积分汇总统计 |

## 奖品与兑换

### 奖品

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/prizes` | 创建奖品 |
| PUT | `/api/prizes/{id}` | 更新奖品 |
| DELETE | `/api/prizes/{id}` | 删除奖品 |
| GET | `/api/prizes` | 查询奖品列表 |
| GET | `/api/prizes/{id}` | 奖品详情 |
| GET | `/api/prizes/available` | 可兑换奖品列表 |
| PUT | `/api/prizes/{id}/stock` | 调整奖品库存 |

### 盲盒

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/blind-boxes` | 创建盲盒 |
| PUT | `/api/blind-boxes/{id}` | 更新盲盒 |
| DELETE | `/api/blind-boxes/{id}` | 删除盲盒 |
| GET | `/api/blind-boxes` | 查询盲盒列表 |
| GET | `/api/blind-boxes/{id}` | 盲盒详情 |
| GET | `/api/blind-boxes/{id}/items` | 盲盒奖品项 |
| POST | `/api/blind-boxes/{id}/items` | 添加盲盒奖品项 |
| DELETE | `/api/blind-boxes/{poolId}/items/{itemId}` | 删除盲盒奖品项 |
| GET | `/api/blind-boxes/available` | 可参与盲盒列表 |

### 兑换

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/exchanges/direct` | 直接兑换奖品 |
| POST | `/api/exchanges/blind-box` | 参与盲盒兑换 |
| GET | `/api/exchanges` | 查询兑换记录 |
| GET | `/api/exchanges/{id}` | 兑换详情 |
| POST | `/api/exchanges/{id}/fulfill` | 核销兑换 |
| POST | `/api/exchanges/{id}/cancel` | 取消兑换 |

## 管理后台

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/health` | 管理后台健康检查 |
| GET | `/api/admin/config` | 获取实例配置 |
| PUT | `/api/admin/config` | 更新实例配置 |
| GET | `/api/admin/accounts` | 查询账户列表 |
| POST | `/api/admin/accounts/{id}/enable` | 启用账户 |
| POST | `/api/admin/accounts/{id}/disable` | 禁用账户 |
| GET | `/api/admin/audit-logs` | 查询审计日志 |
| GET | `/api/admin/audit-logs/export` | 导出审计日志 |

## 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 服务健康检查（无需认证） |
| GET | `/api/instance/status` | 实例初始化状态（无需认证） |

## 更多说明

- 接口权限由角色（`admin` / `parent` / `child`）和家族资源隔离共同控制。
- 写操作（POST/PUT/DELETE）需要有效的 CSRF 令牌。
- 部分接口支持幂等键（`idempotencyKey`），防止重复提交。
- 详细请求/响应字段定义请参考各 Controller 源码：`server/*/src/main/java/com/cutegoals/**/controller/`。

import { Alova } from '@/utils/http/alova';
import type {
  Account,
  AccountListParams,
  AdminInitParams,
  AuditLog,
  AuditLogParams,
  ConfigEntry,
  ConfigUpdatePayload,
  HealthData,
  InstanceStatus,
  LoginResult,
  PageResult,
} from '@/types/api';

/**
 * GET /api/instance/status — 实例初始化状态/概览
 * Source: server/web/.../controller/HealthController.java
 * （旧版 AdminInitPage/AdminOverview 使用：instanceStatus === 'INITIALIZED' 表示已初始化）
 */
export function getInstanceStatus() {
  return Alova.Get<InstanceStatus>('/instance/status', {
    meta: { ignoreToken: true },
  });
}

/**
 * POST /api/auth/initialize — 管理员初始化（INIT_TOKEN + 手机号 + 密码）
 * 成功后自动登录并写入会话 Cookie，返回 { accountId, phone, roles, familyId, initialized, expiresIn }
 * Source: server/web/.../controller/InitializeController.java
 */
export function adminInit(params: AdminInitParams) {
  return Alova.Post<LoginResult>('/auth/initialize', params, {
    meta: { ignoreToken: true, quiet: true },
  });
}

/**
 * GET /api/admin/config — 系统配置项列表（敏感项 value 为 null、masked=true）
 * Source: server/instance-management/.../controller/InstanceConfigController.java
 */
export function getConfig() {
  return Alova.Get<ConfigEntry[]>('/admin/config');
}

/**
 * PUT /api/admin/config — 更新系统配置（仅提交变更项 { key: value }）
 */
export function updateConfig(payload: ConfigUpdatePayload) {
  return Alova.Put<void>('/admin/config', payload);
}

/**
 * GET /api/admin/accounts — 账号列表（page 从 1 开始，后端默认 1）
 * Source: server/instance-management/.../controller/AccountManagementController.java
 */
export function listAccounts(params?: AccountListParams) {
  return Alova.Get<PageResult<Account>>('/admin/accounts', {
    params,
  });
}

/** POST /api/admin/accounts/{id}/enable */
export function enableAccount(id: string | number) {
  return Alova.Post<void>(`/admin/accounts/${id}/enable`);
}

/** POST /api/admin/accounts/{id}/disable */
export function disableAccount(id: string | number) {
  return Alova.Post<void>(`/admin/accounts/${id}/disable`);
}

/**
 * GET /api/admin/audit-logs — 审计日志（page 从 1 开始）
 * Source: server/instance-management/.../controller/AuditLogController.java
 */
export function listAuditLogs(params?: AuditLogParams) {
  return Alova.Get<PageResult<AuditLog>>('/admin/audit-logs', {
    params,
  });
}

/**
 * GET /api/admin/health — 健康面板（数据库/备份/恢复演练指标）
 * Source: server/instance-management/.../controller/AdminHealthController.java
 */
export function getHealth() {
  return Alova.Get<HealthData>('/admin/health');
}

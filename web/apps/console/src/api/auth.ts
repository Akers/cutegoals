import { Alova } from '@/utils/http/alova';
import type { LoginParams, LoginResult, MeResult } from '@/types/api';

/**
 * POST /api/auth/login — 手机号 + 密码登录（家长/管理员共用）
 * 服务端通过 Set-Cookie 写入 access/refresh token（HttpOnly）与 csrf_token
 * Source: server/web/.../controller/AuthController.java
 */
export function login(params: LoginParams) {
  return Alova.Post<LoginResult>('/auth/login', params, {
    meta: { ignoreToken: true, quiet: true },
  });
}

/**
 * GET /api/auth/me — 当前登录账号信息（用于应用启动时恢复会话）
 */
export function getMe() {
  return Alova.Get<MeResult>('/auth/me', {
    meta: { ignoreToken: true },
  });
}

/**
 * POST /api/auth/logout — 注销当前会话（服务端撤销会话并清除 Cookie）
 */
export function logout() {
  return Alova.Post<void>('/auth/logout');
}

/**
 * PUT /api/auth/password — 修改当前账号密码
 */
export function changePassword(params: { oldPassword: string; newPassword: string }) {
  return Alova.Put<void>('/auth/password', params);
}

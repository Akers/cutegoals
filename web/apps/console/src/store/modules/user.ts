import { defineStore } from 'pinia';
import { store } from '@/store';
import { PageEnum } from '@/enums/pageEnum';

import { getMe as getMeApi, login as loginApi, logout as logoutApi } from '@/api/auth';
import { adminInit as adminInitApi } from '@/api/admin';
import type { AdminInitParams, LoginParams } from '@/types/api';

/**
 * 会话账号信息：登录返回 { accountId, phone, roles, familyId, expiresIn }，
 * /auth/me 返回 { accountId, roles, childId?, familyId? }。
 * 会话仅靠 Cookie 维持，本地不持久化任何 token。
 */
export interface AccountInfo {
  accountId: string;
  phone?: string;
  roles: string[];
  childId?: string | null;
  familyId?: string | null;
  expiresIn?: number;
}

export type UserArea = 'parent' | 'admin';

const BACKEND_ROLE_MAP: Record<string, string> = {
  INSTANCE_ADMIN: 'admin',
  PARENT: 'parent',
  CHILD: 'child',
};

function normalizeRoles(roles?: string[]): string[] {
  if (!roles) return [];
  const result: string[] = [];
  for (const raw of roles) {
    if (typeof raw !== 'string') continue;
    const mapped = BACKEND_ROLE_MAP[raw.toUpperCase().replace(/^ROLE_/, '')];
    if (mapped && !result.includes(mapped)) result.push(mapped);
  }
  return result;
}

export interface IUserState {
  info: AccountInfo | null;
  roles: string[];
}

export const useUserStore = defineStore({
  id: 'app-user',
  state: (): IUserState => ({
    info: null,
    roles: [],
  }),
  getters: {
    authed(): boolean {
      return this.roles.length > 0;
    },
    isAdmin(): boolean {
      return this.roles.includes('admin');
    },
    isParent(): boolean {
      return this.roles.includes('parent');
    },
    getNickname(): string {
      const phone = this.info?.phone;
      if (phone) {
        return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
      }
      return this.isAdmin ? '管理员' : '用户';
    },
    getAccountInfo(): AccountInfo | null {
      return this.info;
    },
    /** 根据角色推导默认首页：实例管理员 → /admin，其余 → /parent */
    homePath(): string {
      return this.isAdmin ? PageEnum.ADMIN_HOME : PageEnum.BASE_HOME;
    },
    /** 根据角色推导所属区域 */
    area(): UserArea | null {
      if (!this.roles.length) return null;
      return this.isAdmin ? 'admin' : 'parent';
    },
  },
  actions: {
    applyAccount(account: AccountInfo) {
      this.info = account;
      this.roles = normalizeRoles(account.roles);
    },
    /** 家长登录（POST /api/auth/login，服务端写入会话 Cookie） */
    async loginParent(params: LoginParams) {
      const account = await loginApi(params);
      this.applyAccount(account);
      return account;
    },
    /** 管理员登录（同一端点，页面层面区分去向） */
    async loginAdmin(params: LoginParams) {
      const account = await loginApi(params);
      this.applyAccount(account);
      return account;
    },
    /** 管理员初始化（POST /api/auth/initialize，成功即自动登录） */
    async adminInit(params: AdminInitParams) {
      const account = await adminInitApi(params);
      this.applyAccount(account);
      return account;
    },
    /** 应用启动时通过 GET /api/auth/me 恢复会话；未登录/过期时抛错 */
    async fetchMe() {
      const me = await getMeApi();
      const merged: AccountInfo = {
        ...this.info,
        ...me,
        roles: me.roles ?? [],
      };
      this.applyAccount(merged);
      return me;
    },
    /** 退出登录：调用登出接口（失败也照常清理本地状态） */
    async logout() {
      try {
        await logoutApi();
      } finally {
        this.reset();
      }
    },
    reset() {
      this.info = null;
      this.roles = [];
    },
  },
});

// Need to be used outside the setup
export function useUser() {
  return useUserStore(store);
}

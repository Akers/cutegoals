// 全局 dayjs 插件注册。必须早于 antd DatePicker / Calendar 渲染，
// 否则 rc-picker 内部调用 `.weekday(...)` 会抛出 "weekday is not a function"。
import '@/shared/dayjs';

import '@/styles/index.css';
import React from 'react';
import { App } from 'antd';
import { AuthProvider, useAuth } from '@/shared/auth';
import { RoleProvider } from '@/shared/RoleContext';
import { normalizeRoles } from '@/shared/role';
import type { Role } from '@/shared/role';

/** Derive the user's primary role (for theme/defaults) from the account's roles list.
 *  An account may hold multiple roles (e.g. the instance admin also holds PARENT);
 *  the primary role only drives theming — route authorization checks the full
 *  role set via `normalizeRoles` (see `wrappers/AuthGuard.tsx`).
 */
function deriveRole(roles?: string[]): Role {
  const normalized = normalizeRoles(roles);
  if (normalized.includes('admin')) return 'admin';
  if (normalized.includes('parent')) return 'parent';
  return 'child';
}

/**
 * Inner component that reads AuthContext (must be inside AuthProvider)
 * and provides the resolved role to RoleContext.
 */
function RoleAwareApp({ children }: { children: React.ReactNode }) {
  const { account } = useAuth();
  const role = deriveRole(account?.roles);
  return <RoleProvider role={role}>{children}</RoleProvider>;
}

/**
 * UmiJS runtime rootContainer.
 * Wraps the app tree with:
 *   AuthProvider → RoleAwareApp (RoleProvider) → antd App
 */
export function rootContainer(container: React.ReactNode) {
  return React.createElement(
    AuthProvider,
    null,
    React.createElement(RoleAwareApp, null, React.createElement(App, null, container)),
  );
}

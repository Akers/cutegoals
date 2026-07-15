import React from 'react';
import { App } from 'antd';
import { AuthProvider, useAuth } from '@/shared/auth';
import { RoleProvider } from '@/shared/RoleContext';
import type { Role } from '@/shared/role';

/** Derive the user's single role from the account's roles list. */
function deriveRole(roles?: string[]): Role {
  if (!roles || roles.length === 0) return 'child';
  if (roles.includes('admin')) return 'admin';
  if (roles.includes('parent')) return 'parent';
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
  return React.createElement(AuthProvider, null,
    React.createElement(RoleAwareApp, null,
      React.createElement(App, null, container),
    ),
  );
}

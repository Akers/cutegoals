import { createContext, useContext } from 'react';

export type Role = 'admin' | 'parent' | 'child';

/** Backend (`AuthConstants`) role string → frontend Role.
 *  `satisfies` gives compile-time protection against typos in mapped values. */
const BACKEND_ROLE_MAP = {
  INSTANCE_ADMIN: 'admin',
  PARENT: 'parent',
  CHILD: 'child',
} as const satisfies Record<string, Role>;

/**
 * Normalize backend role strings (e.g. `INSTANCE_ADMIN`, `ROLE_PARENT`)
 * into the frontend Role set. Unknown entries are ignored; result is deduplicated.
 * An account may legitimately hold multiple roles (e.g. the instance admin
 * also holds PARENT), so callers must treat this as a set, not a single role.
 */
export function normalizeRoles(roles?: string[]): Role[] {
  if (!roles) return [];
  const result: Role[] = [];
  for (const raw of roles) {
    if (typeof raw !== 'string') continue;
    const mapped = BACKEND_ROLE_MAP[raw.toUpperCase().replace(/^ROLE_/, '') as keyof typeof BACKEND_ROLE_MAP];
    if (mapped && !result.includes(mapped)) result.push(mapped);
  }
  return result;
}

interface RoleContextValue {
  role: Role;
}

const RoleContext = createContext<RoleContextValue>({ role: 'child' });

export function useRole(): RoleContextValue {
  return useContext(RoleContext);
}

export { RoleContext };

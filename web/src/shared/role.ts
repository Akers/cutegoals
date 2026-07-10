import { createContext, useContext } from 'react';

export type Role = 'admin' | 'parent' | 'child';

interface RoleContextValue {
  role: Role;
}

const RoleContext = createContext<RoleContextValue>({ role: 'child' });

export function useRole(): RoleContextValue {
  return useContext(RoleContext);
}

export { RoleContext };

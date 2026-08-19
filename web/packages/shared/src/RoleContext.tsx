import { useEffect } from 'react';
import { RoleContext, type Role } from './role';
import { setDocumentTheme } from './theme';

export function RoleProvider({
  role,
  children,
}: {
  role: Role;
  children: React.ReactNode;
}) {
  useEffect(() => {
    setDocumentTheme(role);
  }, [role]);

  return (
    <RoleContext.Provider value={{ role }}>
      {children}
    </RoleContext.Provider>
  );
}

export { RoleContext };
export type { Role } from './role';

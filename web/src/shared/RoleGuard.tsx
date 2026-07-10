import { type ReactNode } from 'react';
import { useRole, type Role } from './role';
import AccessDenied from './AccessDenied';

interface RoleGuardProps {
  role: Role;
  children: ReactNode;
}

function RoleGuard({ role, children }: RoleGuardProps) {
  const { role: currentRole } = useRole();

  if (currentRole !== role) {
    return <AccessDenied />;
  }

  return <>{children}</>;
}

export default RoleGuard;

export const APP_NAME = 'CuteGoals';

export interface RoleInfo {
  role: 'admin' | 'parent' | 'child';
  label: string;
}

export function createRoleInfo(role: RoleInfo['role']): RoleInfo {
  const labels: Record<RoleInfo['role'], string> = {
    admin: '管理后台',
    parent: '家长端',
    child: '儿童端',
  };
  return { role, label: labels[role] };
}

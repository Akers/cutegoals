/**
 * UmiJS Access 定义文件。
 *
 * 由于 access() 是纯函数（非 React 组件），无法直接使用 useRole() hook。
 * 而 getInitialState() 运行时机早于 rootContainer（AuthProvider/RoleProvider 尚未挂载），
 * 也无法从 AuthContext 获取角色。
 *
 * 因此采用 URL pathname 推导方案：
 *   路由前缀 → 期望角色 → 对应权限
 *
 * 此方案与 AuthGuard wrapper 中的角色推导逻辑（AuthGuard.tsx:41-47）保持一致。
 */
export default function access() {
  const pathname = typeof window !== 'undefined' ? window.location.pathname : '';
  const role: 'admin' | 'parent' | 'child' = pathname.startsWith('/admin')
    ? 'admin'
    : pathname.startsWith('/child')
      ? 'child'
      : 'parent';

  return {
    canAdmin: role === 'admin',
    canParent: role === 'parent',
    canChild: role === 'child',
  };
}

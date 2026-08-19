import { Outlet, Navigate } from 'umi';
import { useAuth } from '@shared/auth';
import { normalizeRoles } from '@shared/role';
import { KidSpinner } from '@/design/components';
import { EmptyState, KidButton } from '@/design/components';

/** 孩子端路由守卫：未登录跳 /child/login；非孩子角色拒绝访问。 */
export default function AuthGuard() {
  const { isAuthenticated, loading, account } = useAuth();

  if (loading) {
    return <KidSpinner />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/child/login" replace />;
  }

  const roles = normalizeRoles(account?.roles);
  if (!roles.includes('child')) {
    return (
      <div className="kid-login-wrap">
        <div className="kid-login-card">
          <EmptyState emoji="🚫" text="无权访问此页面" />
          <KidButton variant="ghost" onClick={() => window.location.assign('/child/login')}>
            返回登录
          </KidButton>
        </div>
      </div>
    );
  }

  return <Outlet />;
}

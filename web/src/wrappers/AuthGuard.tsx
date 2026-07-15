import { Outlet, useLocation, Navigate } from 'umi';
import { useAuth } from '@/shared/auth';
import { useRole } from '@/shared/role';
import { Result, Spin } from 'antd';
import type { Role } from '@/shared/role';

export default function AuthGuard() {
  const { isAuthenticated, loading } = useAuth();
  const { role } = useRole();
  const location = useLocation();

  // Auth still resolving — show spinner
  if (loading) {
    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: 300,
        }}
      >
        <Spin size="large" />
      </div>
    );
  }

  // Not authenticated → redirect to the correct login page
  if (!isAuthenticated) {
    const pathname = location.pathname;
    let loginPath = '/child/login';
    if (pathname.startsWith('/admin')) {
      loginPath = '/admin/login';
    } else if (pathname.startsWith('/parent')) {
      loginPath = '/parent/login';
    }
    return <Navigate to={loginPath} replace />;
  }

  // Determine which role this route section expects
  const pathname = location.pathname;
  let expectedRole: Role = 'child';
  if (pathname.startsWith('/admin')) {
    expectedRole = 'admin';
  } else if (pathname.startsWith('/parent')) {
    expectedRole = 'parent';
  }

  // Role mismatch → 403
  if (role !== expectedRole) {
    return <Result status="403" subTitle="无权访问此页面" />;
  }

  // All checks pass → render child route
  return <Outlet />;
}

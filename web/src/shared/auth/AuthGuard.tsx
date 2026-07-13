import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@shared/auth';
import { useRole } from '@shared/role';
import { LoadingState } from '@shared/components';

interface AuthGuardProps {
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

export function AuthGuard({ children, fallback }: AuthGuardProps) {
  const { isAuthenticated, loading } = useAuth();
  const { role } = useRole();
  const location = useLocation();

  if (loading) return <LoadingState />;

  if (!isAuthenticated) {
    const loginPath =
      role === 'admin' ? '/admin/login' : role === 'parent' ? '/parent/login' : '/child/bind';
    return <Navigate to={loginPath} state={{ from: location }} replace />;
  }

  return <>{fallback ?? children}</>;
}

export function LogoutConfirm({ onCancel, onConfirm }: { onCancel: () => void; onConfirm: () => void }) {
  return (
    <div className="cg-page flex min-h-screen flex-col items-center justify-center" role="alertdialog">
      <div className="w-full max-w-sm cg-card p-6">
        <h2 className="mb-2 text-xl font-bold text-cg-text">确认登出？</h2>
        <p className="mb-6 text-cg-text-muted">登出后需要重新登录。</p>
        <div className="flex gap-2">
          <button
            onClick={onCancel}
            className="flex-1 rounded-cg-md bg-cg-surface-raised px-4 py-2 text-cg-text hover:bg-cg-border min-h-touch"
          >
            取消
          </button>
          <button
            onClick={onConfirm}
            className="flex-1 rounded-cg-md bg-cg-danger px-4 py-2 text-cg-danger-text hover:bg-cg-danger-hover min-h-touch"
          >
            登出
          </button>
        </div>
      </div>
    </div>
  );
}

export function SessionExpired() {
  return (
    <div className="cg-page flex min-h-screen flex-col items-center justify-center">
      <div className="w-full max-w-sm cg-card p-6 text-center">
        <h2 className="mb-2 text-xl font-bold text-cg-text">会话已过期</h2>
        <p className="mb-6 text-cg-text-muted">请重新登录以继续使用。</p>
        <a
          href="/"
          className="inline-flex min-h-touch items-center justify-center rounded-cg-md bg-cg-primary px-4 py-2 text-cg-primary-text"
        >
          返回首页
        </a>
      </div>
    </div>
  );
}

export default AuthGuard;

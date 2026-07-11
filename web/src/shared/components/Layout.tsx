import { ReactNode, useState } from 'react';
import { Link } from 'react-router-dom';
import { useRole } from '@shared/role';
import { useAuth } from '@shared/auth';
import { ConfirmModal } from './Modal';

interface PageHeaderProps {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  backTo?: string;
}

export function PageHeader({ title, subtitle, actions, backTo }: PageHeaderProps) {
  return (
    <div className="mb-6 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-center gap-2">
        {backTo && (
          <Link
            to={backTo}
            className="inline-flex items-center justify-center gap-2 rounded-cg-md px-3 py-2 text-sm font-medium text-cg-text hover:bg-cg-surface-raised focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cg-focus min-h-touch"
            aria-label="返回"
          >
            ← 返回
          </Link>
        )}
        <div>
          <h1 className="text-2xl font-bold text-cg-text">{title}</h1>
          {subtitle && <p className="text-sm text-cg-text-muted">{subtitle}</p>}
        </div>
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Layout({ children }: { children: ReactNode }) {
  const { role } = useRole();
  const { isAuthenticated, logout } = useAuth();
  const home = `/${role}`;
  const [showLogout, setShowLogout] = useState(false);

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b border-cg-border bg-cg-surface/95 backdrop-blur">
        <div className="cg-page flex items-center justify-between py-3">
          <Link to={home} className="text-lg font-bold text-cg-primary">
            CuteGoals
          </Link>
          <nav aria-label="主导航" className="flex items-center gap-2">
            <NavLinks role={role} />
            {isAuthenticated && (
              <button
                onClick={() => setShowLogout(true)}
                className="rounded-cg-md px-3 py-2 text-sm font-medium text-cg-text hover:bg-cg-surface-raised focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cg-focus min-h-touch inline-flex items-center"
                aria-label="登出"
              >
                登出
              </button>
            )}
          </nav>
        </div>
      </header>
      <main className="flex-1">
        <div className="cg-page">{children}</div>
      </main>
      <footer className="border-t border-cg-border bg-cg-surface py-4">
        <div className="cg-page text-center text-xs text-cg-text-muted">
          CuteGoals 2.0 · {role === 'admin' ? '管理后台' : role === 'parent' ? '家长端' : '儿童端'}
        </div>
      </footer>
      <ConfirmModal
        isOpen={showLogout}
        onClose={() => setShowLogout(false)}
        title="确认登出"
        message="登出后需要重新登录。"
        confirmText="登出"
        confirmVariant="danger"
        onConfirm={async () => {
          await logout();
          setShowLogout(false);
        }}
      />
    </div>
  );
}

function NavLinks({ role }: { role: string }) {
  if (role === 'admin') {
    return (
      <>
        <NavLink to="/admin">概览</NavLink>
        <NavLink to="/admin/accounts">账号</NavLink>
        <NavLink to="/admin/audit">审计</NavLink>
        <NavLink to="/admin/health">健康</NavLink>
      </>
    );
  }

  if (role === 'parent') {
    return (
      <>
        <NavLink to="/parent">家庭</NavLink>
        <NavLink to="/parent/tasks">任务</NavLink>
        <NavLink to="/parent/reviews">审核</NavLink>
        <NavLink to="/parent/points">积分</NavLink>
        <NavLink to="/parent/exchanges">兑换</NavLink>
      </>
    );
  }

  return (
    <>
      <NavLink to="/child">今日</NavLink>
      <NavLink to="/child/prizes">奖品</NavLink>
      <NavLink to="/child/blind-boxes">盲盒</NavLink>
      <NavLink to="/child/exchanges">历史</NavLink>
    </>
  );
}

function NavLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link
      to={to}
      className="rounded-cg-md px-3 py-2 text-sm font-medium text-cg-text hover:bg-cg-surface-raised focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cg-focus min-h-touch inline-flex items-center"
    >
      {children}
    </Link>
  );
}

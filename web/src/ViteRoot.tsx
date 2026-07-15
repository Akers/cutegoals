import { lazy, Suspense } from 'react';
import { useLocation } from 'umi';
import AccessDenied from '@shared/AccessDenied';
import { useRole } from '@shared/role';
import { Spin } from 'antd';
import { getRoleFromPath } from '@shared/theme';

const AdminApp = lazy(() => import('@admin/App'));
const ParentApp = lazy(() => import('@parent/App'));
const ChildApp = lazy(() => import('@child/App'));

function App() {
  const { role } = useRole();
  const { pathname } = useLocation();
  const expectedRole = getRoleFromPath(pathname);

  if (role !== expectedRole) {
    return <AccessDenied />;
  }

  const RoleApp = role === 'admin' ? AdminApp : role === 'parent' ? ParentApp : ChildApp;

  return (
    <Suspense fallback={<Spin className="flex justify-center py-12" />}>
      <RoleApp />
    </Suspense>
  );
}

export default App;

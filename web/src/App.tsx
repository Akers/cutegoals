import { lazy, Suspense } from 'react';
import { useLocation } from 'react-router-dom';
import AccessDenied from '@shared/AccessDenied';
import { useRole } from '@shared/role';
import { ToastProvider, LoadingState } from '@shared/components';
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
    <ToastProvider>
      <Suspense fallback={<LoadingState />}>
        <RoleApp />
      </Suspense>
    </ToastProvider>
  );
}

export default App;

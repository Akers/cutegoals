import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import RoleGuard from '@shared/RoleGuard';
import NotFound from '@shared/NotFound';

const AdminApp = lazy(() => import('@admin/App'));
const ParentApp = lazy(() => import('@parent/App'));
const ChildApp = lazy(() => import('@child/App'));

function App() {
  return (
    <Suspense fallback={<div data-testid="loading-fallback">加载中...</div>}>
      <Routes>
        <Route path="/" element={<Navigate to="/child" replace />} />
        <Route
          path="/admin/*"
          element={
            <RoleGuard role="admin">
              <AdminApp />
            </RoleGuard>
          }
        />
        <Route
          path="/parent/*"
          element={
            <RoleGuard role="parent">
              <ParentApp />
            </RoleGuard>
          }
        />
        <Route
          path="/child/*"
          element={
            <RoleGuard role="child">
              <ChildApp />
            </RoleGuard>
          }
        />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  );
}

export default App;

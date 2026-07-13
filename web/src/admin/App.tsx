import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthGuard } from '@shared/auth/AuthGuard';
import {
  AdminAccountsPage,
  AdminAuditPage,
  AdminConfigPage,
  AdminHealthPage,
  AdminOverviewPage,
} from './pages';
import { AdminInitPage } from './pages/AdminInitPage';
import { AdminLoginPage } from './pages/AdminLoginPage';

function AdminApp() {
  return (
    <Routes>
      <Route path="/admin/init" element={<AdminInitPage />} />
      <Route path="/admin/login" element={<AdminLoginPage />} />
      <Route
        path="/admin"
        element={
          <AuthGuard>
            <AdminOverviewPage />
          </AuthGuard>
        }
      />
      <Route
        path="/admin/config"
        element={
          <AuthGuard>
            <AdminConfigPage />
          </AuthGuard>
        }
      />
      <Route
        path="/admin/accounts"
        element={
          <AuthGuard>
            <AdminAccountsPage />
          </AuthGuard>
        }
      />
      <Route
        path="/admin/audit"
        element={
          <AuthGuard>
            <AdminAuditPage />
          </AuthGuard>
        }
      />
      <Route
        path="/admin/health"
        element={
          <AuthGuard>
            <AdminHealthPage />
          </AuthGuard>
        }
      />
      <Route path="*" element={<Navigate to="/admin" replace />} />
    </Routes>
  );
}

export default AdminApp;

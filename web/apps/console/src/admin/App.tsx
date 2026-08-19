import { AuthGuard } from '@shared/auth/AuthGuard';
import { AdminOverviewPage } from './pages';

function AdminApp() {
  return (
    <AuthGuard>
      <AdminOverviewPage />
    </AuthGuard>
  );
}

export default AdminApp;

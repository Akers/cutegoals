import { AuthGuard } from '@shared/auth/AuthGuard';
import { ChildHomePage } from './pages';

function ChildApp() {
  return (
    <AuthGuard>
      <ChildHomePage />
    </AuthGuard>
  );
}

export default ChildApp;

import { AuthGuard } from '@shared/auth/AuthGuard';
import { ParentHomePage } from './pages';

function ParentApp() {
  return (
    <AuthGuard>
      <ParentHomePage />
    </AuthGuard>
  );
}

export default ParentApp;

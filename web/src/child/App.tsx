import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthGuard } from '@shared/auth/AuthGuard';
import { ChildBindPage } from './pages/ChildBindPage';
import { ChildLoginPage } from './pages/ChildLoginPage';
import {
  ChildHomePage,
  ChildTasksPage,
  ChildPrizesPage,
  ChildBlindBoxesPage,
  ChildExchangesPage,
} from './pages';

function ChildApp() {
  return (
    <Routes>
      <Route path="/child/bind" element={<ChildBindPage />} />
      <Route path="/child/login" element={<ChildLoginPage />} />
      <Route
        path="/child"
        element={
          <AuthGuard>
            <ChildHomePage />
          </AuthGuard>
        }
      />
      <Route
        path="/child/tasks"
        element={
          <AuthGuard>
            <ChildTasksPage />
          </AuthGuard>
        }
      />
      <Route
        path="/child/prizes"
        element={
          <AuthGuard>
            <ChildPrizesPage />
          </AuthGuard>
        }
      />
      <Route
        path="/child/blind-boxes"
        element={
          <AuthGuard>
            <ChildBlindBoxesPage />
          </AuthGuard>
        }
      />
      <Route
        path="/child/exchanges"
        element={
          <AuthGuard>
            <ChildExchangesPage />
          </AuthGuard>
        }
      />
      <Route path="*" element={<Navigate to="/child" replace />} />
    </Routes>
  );
}

export default ChildApp;

import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthGuard } from '@shared/auth/AuthGuard';
import {
  ParentBlindBoxesPage,
  ParentChildrenPage,
  ParentExchangesPage,
  ParentFamilyPage,
  ParentHomePage,
  ParentPointsPage,
  ParentPrizesPage,
  ParentReviewsPage,
  ParentTasksPage,
  ParentTemplatesPage,
} from './pages';
import { ParentLoginPage } from './pages/ParentLoginPage';

function ParentApp() {
  return (
    <Routes>
      <Route path="/parent/login" element={<ParentLoginPage />} />
      <Route
        path="/parent"
        element={
          <AuthGuard>
            <ParentHomePage />
          </AuthGuard>
        }
      />
      <Route
        path="/parent/family"
        element={
          <AuthGuard>
            <ParentFamilyPage />
          </AuthGuard>
        }
      />
      <Route
        path="/parent/children"
        element={
          <AuthGuard>
            <ParentChildrenPage />
          </AuthGuard>
        }
      />
      <Route
        path="/parent/templates"
        element={
          <AuthGuard>
            <ParentTemplatesPage />
          </AuthGuard>
        }
      />
      <Route
        path="/parent/tasks"
        element={
          <AuthGuard>
            <ParentTasksPage />
          </AuthGuard>
        }
      />
      <Route
        path="/parent/reviews"
        element={
          <AuthGuard>
            <ParentReviewsPage />
          </AuthGuard>
        }
      />
      <Route
        path="/parent/points"
        element={
          <AuthGuard>
            <ParentPointsPage />
          </AuthGuard>
        }
      />
      <Route
        path="/parent/prizes"
        element={
          <AuthGuard>
            <ParentPrizesPage />
          </AuthGuard>
        }
      />
      <Route
        path="/parent/blind-boxes"
        element={
          <AuthGuard>
            <ParentBlindBoxesPage />
          </AuthGuard>
        }
      />
      <Route
        path="/parent/exchanges"
        element={
          <AuthGuard>
            <ParentExchangesPage />
          </AuthGuard>
        }
      />
      <Route path="*" element={<Navigate to="/parent" replace />} />
    </Routes>
  );
}

export default ParentApp;

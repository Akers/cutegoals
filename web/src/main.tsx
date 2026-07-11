import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { RoleProvider } from '@shared/RoleContext';
import { AuthProvider } from '@shared/auth';
import { getRoleFromPath } from '@shared/theme';
import './styles/index.css';

const role = getRoleFromPath(window.location.pathname);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RoleProvider role={role}>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </RoleProvider>
  </StrictMode>,
);

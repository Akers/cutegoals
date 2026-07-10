import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { RoleProvider } from '@shared/RoleContext';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RoleProvider role="child">
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </RoleProvider>
  </StrictMode>,
);

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import ChildApp from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ChildApp />
  </StrictMode>,
);

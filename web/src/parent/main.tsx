import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import ParentApp from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ParentApp />
  </StrictMode>,
);

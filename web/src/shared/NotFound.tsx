import { useContext } from 'react';
import { Navigate } from 'react-router-dom';
import { RoleContext } from './role';

export default function NotFound() {
  const { role } = useContext(RoleContext);

  // Unknown routes: redirect to the current role's home page.
  // If no role is set (should not happen in practice), fallback to /child.
  return <Navigate to={`/${role}`} replace />;
}

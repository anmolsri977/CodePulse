import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

/**
 * Route wrapper that prevents authenticated users from viewing public-only pages
 * like /login and /register, redirecting them directly to their role dashboard.
 */
const PublicOnlyRoute = () => {
  const { isAuthenticated: authed, user } = useAuth();

  if (authed) {
    const redirectPath = user?.role === 'TEACHER' ? '/teacher' : '/student';
    return <Navigate to={redirectPath} replace />;
  }

  return <Outlet />;
};

export default PublicOnlyRoute;

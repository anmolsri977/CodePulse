import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

/**
 * Route wrapper that ensures the user is authenticated.
 * Optionally enforces role-based authorization.
 */
const ProtectedRoute = ({ allowedRoles }) => {
  const location = useLocation();
  const { isAuthenticated: authed, user } = useAuth();

  if (!authed) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (allowedRoles && user?.role && !allowedRoles.includes(user.role)) {
    // If authenticated user tries to access a role-restricted route, redirect to their home
    const redirectPath = user.role === 'TEACHER' ? '/teacher' : '/student';
    return <Navigate to={redirectPath} replace />;
  }

  return <Outlet />;
};

export default ProtectedRoute;
